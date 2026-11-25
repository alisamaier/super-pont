package ro.superpont;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.model.User;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.service.VerificationEmailSender;
import ro.superpont.common.DomainException;
import ro.superpont.football.sportstats.client.*;
import ro.superpont.football.sportstats.service.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_SPORTSTATS_DATABASE_URL:jdbc:h2:mem:sportstats;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
    "spring.flyway.placeholders.leaderboardDemo=true", "app.sportstats.sync-enabled=false",
    "app.team-logos.cache-dir=./build/test-logo-cache",
    "app.sportstats.phase-divisions=dx2=REGULAR,playoff=PLAY_OFF,playout=PLAY_OUT"
})
@ActiveProfiles({"dev", "test"})
class SportStatsSyncTests {
    static final LocalDate FROM = LocalDate.parse("2026-11-15"), TO = LocalDate.parse("2026-12-06");
    static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    static final String EMAIL = "sportstats-test@example.test", PASSWORD = "Sportstats-test-2026!";
    @LocalServerPort int port;
    @Autowired SportStatsSyncService sync;
    @Autowired SportStatsLogoService logos;
    @Autowired ro.superpont.football.team.service.TeamLogoCache logoCache;
    @Autowired ro.superpont.football.team.service.FileTeamLogoStore fileLogos;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ApplicationContext context;
    @MockitoBean SportStatsClient client;
    @MockitoBean Clock clock;
    @MockitoBean AuthRateLimiter limits;
    @MockitoBean VerificationEmailSender mail;
    UUID userId;
    List<JsonNode> data;

    @BeforeEach
    void reset() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("DELETE FROM predictions WHERE match_id IN (SELECT id FROM football_matches WHERE provider='SPORTSTATS')");
        jdbc.update("DELETE FROM football_matches WHERE provider='SPORTSTATS'");
        jdbc.update("DELETE FROM rounds WHERE provider='SPORTSTATS'");
        jdbc.update("DELETE FROM competition_phases WHERE provider='SPORTSTATS'");
        jdbc.update("DELETE FROM season_teams WHERE season_id IN (SELECT id FROM seasons WHERE provider='SPORTSTATS')");
        jdbc.update("DELETE FROM seasons WHERE provider='SPORTSTATS'");
        jdbc.update("DELETE FROM teams WHERE provider='SPORTSTATS'");
        jdbc.update("DELETE FROM competitions WHERE provider='SPORTSTATS'");
        jdbc.update("UPDATE sportstats_sync_state SET lock_owner=NULL,locked_until=NULL,retry_after=NULL");
        var user = users.findByEmail(EMAIL).orElseGet(() -> {
            var item = new User("SportStats test", EMAIL, passwords.encode(PASSWORD));
            item.verifyEmail(); item.setHidePredictionsUntilKickoff(true);
            return users.saveAndFlush(item);
        });
        userId = user.getId();
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", userId);
        try (var input = new ClassPathResource("sportstats/superliga-real-response.json").getInputStream()) {
            data = new ArrayList<>();
            for (var item : json.readTree(input).get("data")) data.add(item);
        }
        batch(data);
    }

    @Test
    void importsRealFixtureTwiceWithoutDuplicatesAndKeepsDemoIdentitiesSeparate() {
        var demo = jdbc.queryForList("SELECT id,round_id,home_team_id,away_team_id,kickoff FROM football_matches WHERE provider='LOCAL' ORDER BY id");
        var first = sync.sync(FROM, TO);
        assertThat(first.created()).isEqualTo(16);
        assertThat(first.errors()).isZero();
        var ids = jdbc.queryForList("SELECT id,external_id FROM football_matches WHERE provider='SPORTSTATS' ORDER BY external_id");
        var again = sync.sync(FROM, TO);
        assertThat(again.created()).isZero(); assertThat(again.updated()).isZero(); assertThat(again.ignored()).isEqualTo(16);
        assertThat(jdbc.queryForList("SELECT id,external_id FROM football_matches WHERE provider='SPORTSTATS' ORDER BY external_id")).isEqualTo(ids);
        for (var table : Map.of("teams",16L,"competitions",1L,"seasons",1L,"competition_phases",1L,"rounds",2L).entrySet())
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table.getKey() + " WHERE provider='SPORTSTATS'", Long.class)).isEqualTo(table.getValue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teams WHERE name='FCSB'", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForList("SELECT id,round_id,home_team_id,away_team_id,kickoff FROM football_matches WHERE provider='LOCAL' ORDER BY id")).isEqualTo(demo);
        assertThat(jdbc.queryForObject("SELECT home_score FROM football_matches WHERE external_id='m93hr'", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT away_score FROM football_matches WHERE external_id='m93hr'", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM teams WHERE external_id='t1ma'", String.class)).isEqualTo("FC Botoșani");
        assertThat(context.getBeansOfType(SportStatsScheduler.class)).isEmpty();
    }

    @Test
    void preservesPredictionsAndRecalculatesFinalCorrectionsAndRevokedResultsThroughExistingApis() throws Exception {
        sync.sync(FROM, TO);
        var matchId = id("football_matches", "m93hu");
        try (var owner = login()) {
            var saved = owner.request("PUT", "/matches/" + matchId + "/prediction", Map.of("homeScore",2,"awayScore",1), owner.csrf());
            assertThat(saved.statusCode()).as(saved.body()).isEqualTo(201);
            var predictionId = json.readTree(saved.body()).get("id").asText();
            var before = jdbc.queryForMap("SELECT id,display_name,email,email_verified,hide_predictions_until_kickoff FROM users WHERE id=?",userId);
            assertThat(points(owner)).isZero();
            var updated = changed("m93hu");
            updated.put("status", "FT").put("status_code", "final");
            ((ObjectNode)updated.get("home_team")).put("score",2);
            ((ObjectNode)updated.get("away_team")).put("score",1);
            batch(List.of(updated)); sync.sync(FROM, TO);
            assertThat(points(owner)).isEqualTo(10);
            assertThat(json.readTree(owner.request("GET", "/predictions/me", null, null).body()).at("/items/0/result/points").asInt()).isEqualTo(10);
            ((ObjectNode)updated.get("home_team")).put("score",1);
            sync.sync(FROM, TO);
            assertThat(points(owner)).isEqualTo(4);
            sync.sync(FROM, TO); assertThat(points(owner)).isEqualTo(4);
            updated.put("status_code","live").put("status","2H"); sync.sync(FROM, TO); assertThat(points(owner)).isZero();
            updated.put("status_code","final").put("status","FT");
            ((ObjectNode)updated.get("home_team")).remove("score");
            assertThat(sync.sync(FROM, TO).warnings()).contains("FINAL_SCORE_MISSING");
            assertThat(points(owner)).isZero();
            assertThat(id("football_matches","m93hu")).isEqualTo(matchId);
            assertThat(jdbc.queryForObject("SELECT id FROM predictions WHERE match_id=? AND user_id=?",UUID.class,matchId,userId).toString()).isEqualTo(predictionId);
            assertThat(jdbc.queryForMap("SELECT id,display_name,email,email_verified,hide_predictions_until_kickoff FROM users WHERE id=?",userId)).isEqualTo(before);
        }
    }

    @Test
    void kickoffUpdatesPreserveIdsAndUseExistingServerClockForEditingAndVisibility() throws Exception {
        sync.sync(FROM, TO);
        UUID match = id("football_matches", "m93hu");
        try (var owner = login(); var viewer = new TestHttpSession(port,json)) {
            assertThat(owner.request("PUT","/matches/"+match+"/prediction",Map.of("homeScore",0,"awayScore",0),owner.csrf()).statusCode()).isEqualTo(201);
            var updated = changed("m93hu"); updated.put("start_at","2026-11-23T15:30:00Z");
            batch(List.of(updated)); sync.sync(FROM,TO);
            when(clock.instant()).thenReturn(Instant.parse("2026-11-23T15:00:00Z"));
            assertThat(owner.request("PUT","/matches/"+match+"/prediction",Map.of("homeScore",2,"awayScore",1),owner.csrf()).statusCode()).isEqualTo(200);
            var login = viewer.request("POST","/auth/login",Map.of("email","demo_ana@leaderboard.example.test","password","SuperPontDemo2026!"),viewer.csrf());
            assertThat(login.statusCode()).isEqualTo(200);
            String path = "/leaderboard/users/"+userId+"/predictions?competitionId="+id("competitions","l60")+"&seasonId="+id("seasons","2026/2027");
            assertThat(json.readTree(viewer.request("GET",path,null,null).body()).at("/predictions/items/0/visibility").asText()).isEqualTo("HIDDEN");
            assertThat(json.readTree(owner.request("GET",path,null,null).body()).at("/predictions/items/0/visibility").asText()).isEqualTo("REVEALED");
            when(clock.instant()).thenReturn(Instant.parse("2026-11-23T15:30:00Z"));
            assertThat(owner.request("PUT","/matches/"+match+"/prediction",Map.of("homeScore",3,"awayScore",1),owner.csrf()).statusCode()).isEqualTo(409);
            assertThat(json.readTree(viewer.request("GET",path,null,null).body()).at("/predictions/items/0/visibility").asText()).isEqualTo("REVEALED");
            assertThat(jdbc.queryForObject("SELECT kickoff FROM football_matches WHERE id=?",java.sql.Timestamp.class,match).toInstant()).isEqualTo(Instant.parse("2026-11-23T15:30:00Z"));
        }
    }

    @Test
    void roundsAreScopedByCompetitionSeasonDivisionAndPhaseAndUnknownDivisionsAreNotGuessed() {
        var regular = changed("m93hs");
        var playoff = regular.deepCopy().put("code","m-playoff").put("division_code","playoff");
        var playout = regular.deepCopy().put("code","m-playout").put("division_code","playout");
        var next = regular.deepCopy().put("code","m-next").put("season","2027/2028").put("start_at","2027-11-20T15:30:00Z");
        var unknown = regular.deepCopy().put("code","m-unmapped").put("division_code","newdivision");
        batch(List.of(regular,playoff,playout,next,unknown));
        var summary = sync.sync(FROM,TO);
        assertThat(summary.created()).isEqualTo(4);
        assertThat(summary.warnings()).contains("PHASE_MAPPING_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rounds WHERE provider='SPORTSTATS' AND round_number=16",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT phase_id) FROM rounds WHERE provider='SPORTSTATS'",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seasons WHERE provider='SPORTSTATS'",Long.class)).isEqualTo(2);
    }

    @Test
    void mapsOfficialStatusesAndNeverTreatsAnUnknownStatusOrMissingFinalScoreAsFinished() {
        var expected = Map.ofEntries(Map.entry("scheduled","SCHEDULED"),Map.entry("live","LIVE"),Map.entry("halftime","LIVE"),
                Map.entry("intermission","LIVE"),Map.entry("final","FINISHED"),Map.entry("final_overtime","FINISHED"),
                Map.entry("final_penalties","FINISHED"),Map.entry("postponed","POSTPONED"),Map.entry("delayed","POSTPONED"),
                Map.entry("cancelled","CANCELLED"),Map.entry("suspended","SUSPENDED"),Map.entry("abandoned","SUSPENDED"),
                Map.entry("awaiting_result","UNKNOWN"),Map.entry("unknown","UNKNOWN"),Map.entry("something_new","UNKNOWN"));
        for (var entry : expected.entrySet()) {
            var row = changed("m93hs").put("status_code",entry.getKey());
            batch(List.of(row)); sync.sync(FROM,TO);
            assertThat(jdbc.queryForObject("SELECT status FROM football_matches WHERE external_id='m93hs'",String.class)).isEqualTo(entry.getValue());
            if (!Set.of("FINISHED","LIVE").contains(entry.getValue()))
                assertThat(jdbc.queryForObject("SELECT home_score FROM football_matches WHERE external_id='m93hs'",Integer.class)).isNull();
        }
        for (String raw : List.of("NS","FT","HT","45+2")) {
            var row = changed("m93hs").put("status",raw); row.remove("status_code");
            batch(List.of(row)); sync.sync(FROM,TO);
            assertThat(jdbc.queryForObject("SELECT status FROM football_matches WHERE external_id='m93hs'",String.class))
                    .isEqualTo(raw.equals("NS") ? "SCHEDULED" : raw.equals("FT") ? "FINISHED" : "LIVE");
        }
    }

    @Test
    void providerErrorsRetainSavedDataAndRetryAfterBlocksManualAndScheduledBypasses() {
        sync.sync(FROM,TO);
        var before = jdbc.queryForList("SELECT * FROM football_matches ORDER BY id");
        when(client.fetch(any(),any())).thenThrow(new SportStatsException("API_RATE_LIMITED",NOW.plusSeconds(120)));
        assertThatThrownBy(() -> sync.sync(FROM,TO)).isInstanceOf(SportStatsException.class).hasMessage("API_RATE_LIMITED");
        assertThat(jdbc.queryForList("SELECT * FROM football_matches ORDER BY id")).isEqualTo(before);
        clearInvocations(client);
        assertThatThrownBy(() -> sync.sync(FROM,TO)).isInstanceOf(SportStatsException.class).hasMessage("RETRY_AFTER");
        verifyNoInteractions(client);
        when(clock.instant()).thenReturn(NOW.plusSeconds(121)); batch(data);
        assertThat(sync.sync(FROM,TO).ignored()).isEqualTo(16);
        var stale = changed("m93hs").put("updated_at","2026-01-01T00:00:00Z"); batch(List.of(stale));
        assertThat(sync.sync(FROM,TO).warnings()).contains("STALE_PROVIDER_RECORD");
    }

    @Test
    void twoServiceInstancesCannotOverlapAndDatabaseConstraintsRejectDuplicateExternalIdentities() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.fetch(any(),any())).thenAnswer(call -> { entered.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return new SportStatsClient.Batch(data,1); });
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = tasks.submit(() -> sync.sync(FROM,TO));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var other = context.getAutowireCapableBeanFactory().createBean(SportStatsSyncService.class);
            assertThatThrownBy(() -> other.sync(FROM,TO)).isInstanceOf(DomainException.class).hasMessageContaining("deja");
            release.countDown(); assertThat(run.get(20,TimeUnit.SECONDS).created()).isEqualTo(16);
        } finally { release.countDown(); }
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO teams(id,country_id,name,short_name,provider,external_id)
                SELECT ?,country_id,'Another display name','Another',provider,external_id FROM teams WHERE external_id='t1ma'
                """,UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCancelledMatchWithUnknownKickoffAndNoVersionKeepsItsLastKickoffAndClearsItsFinalScore() {
        sync.sync(FROM,TO);
        var before = jdbc.queryForObject("SELECT kickoff FROM football_matches WHERE external_id='m93hs'",java.sql.Timestamp.class);
        var cancelled = changed("m93hs").put("status_code","cancelled");
        cancelled.putNull("start_at"); cancelled.remove("updated_at");
        batch(List.of(cancelled));
        assertThat(sync.sync(FROM,TO).updated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kickoff FROM football_matches WHERE external_id='m93hs'",java.sql.Timestamp.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM football_matches WHERE external_id='m93hs'",String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT home_score FROM football_matches WHERE external_id='m93hs'",Integer.class)).isNull();
    }

    @Test
    void manualSyncRequiresVerifiedAdministratorAndCsrfWhileAutomaticSyncStaysOff() throws Exception {
        try (var guest = new TestHttpSession(port,json); var user = login()) {
            var body = Map.of("dateFrom",FROM.toString(),"dateTo",TO.toString());
            assertThat(guest.request("POST","/admin/sportstats/sync",body,guest.csrf()).statusCode()).isEqualTo(401);
            assertThat(user.request("POST","/admin/sportstats/sync",body,user.csrf()).statusCode()).isEqualTo(403);
            verifyNoInteractions(client);
        }
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",userId);
        try (var admin = login()) {
            var body = Map.of("dateFrom",FROM.toString(),"dateTo",TO.toString());
            assertThat(admin.request("POST","/admin/sportstats/sync",body,null).statusCode()).isEqualTo(403);
            var result = admin.request("POST","/admin/sportstats/sync",body,admin.csrf());
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
            assertThat(json.readTree(result.body()).get("created").asInt()).isEqualTo(16);
        }
    }

    @Test void logoCatalogUsesExactProviderIdentityKeepsUuidsAndReusesCacheWithoutHttpCalls() throws Exception {
        sync.sync(FROM, TO); prepareLogos();
        var ids = jdbc.queryForList("SELECT id,external_id FROM teams ORDER BY id");
        assertThat(logos.refresh(false).downloaded()).isEqualTo(16);
        assertThat(jdbc.queryForList("SELECT id,external_id FROM teams ORDER BY id")).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT logo_credit FROM teams WHERE external_id='t1nc'", String.class)).isEqualTo("Credit t1nc");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teams WHERE provider='LOCAL' AND logo_hash IS NOT NULL", Long.class)).isZero();
        clearInvocations(client);
        assertThat(logos.refresh(false).downloaded()).isZero();
        verifyNoInteractions(client);
        sync.sync(FROM, TO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teams WHERE logo_hash IS NOT NULL", Long.class)).isEqualTo(16);
    }

    @Test void failedInvalidAndAbsentReplacementsKeepPreviousImageAndMetadata() throws Exception {
        sync.sync(FROM, TO); prepareLogos(); logos.refresh(false);
        var before = jdbc.queryForList("SELECT id,logo_hash,logo_source_url,logo_credit FROM teams ORDER BY id");
        when(client.downloadLogo(any())).thenReturn(new SportStatsClient.Image("invalid PNG".getBytes(), "image/png"));
        assertThat(logos.refresh(true).errors()).isEqualTo(16);
        assertThat(jdbc.queryForList("SELECT id,logo_hash,logo_source_url,logo_credit FROM teams ORDER BY id")).isEqualTo(before);
        jdbc.update("UPDATE teams SET logo_retry_after=NULL");
        when(client.downloadLogo(any())).thenThrow(new SportStatsException("LOGO_NOT_FOUND"));
        assertThat(logos.refresh(true).missing()).isEqualTo(16);
        assertThat(jdbc.queryForList("SELECT id,logo_hash,logo_source_url,logo_credit FROM teams ORDER BY id")).isEqualTo(before);
        clearInvocations(client); logos.refresh(true); verifyNoInteractions(client);
    }

    @Test void logoRateLimitSharesBackoffWithMatchImportAndProtectsExistingFiles() throws Exception {
        sync.sync(FROM, TO); prepareLogos(); logos.refresh(false);
        var before = jdbc.queryForList("SELECT id,logo_hash FROM teams ORDER BY id");
        when(client.downloadLogo(any())).thenThrow(new SportStatsException("API_RATE_LIMITED", NOW.plusSeconds(180)));
        assertThatThrownBy(() -> logos.refresh(true)).hasMessage("API_RATE_LIMITED");
        clearInvocations(client);
        assertThatThrownBy(() -> logos.refresh(true)).hasMessage("RETRY_AFTER");
        assertThatThrownBy(() -> sync.sync(FROM, TO)).hasMessage("RETRY_AFTER");
        verifyNoInteractions(client);
        assertThat(jdbc.queryForList("SELECT id,logo_hash FROM teams ORDER BY id")).isEqualTo(before);
    }

    @Test void logosNeverMatchByNameAndMissingImagesCanBeRecoveredFromPersistentMetadata() throws Exception {
        sync.sync(FROM, TO); prepareLogos();
        when(client.fetchTeams("l60")).thenReturn(List.of(json.readTree("{\"code\":\"unrelated\",\"name\":\"FCSB\"}")));
        assertThat(logos.refresh(false).missing()).isEqualTo(16);
        verify(client, never()).downloadLogo(any());
        jdbc.update("UPDATE teams SET logo_retry_after=NULL"); prepareLogos(); logos.refresh(false);
        var hash = jdbc.queryForObject("SELECT logo_hash FROM teams WHERE external_id='t1nc'", String.class);
        java.nio.file.Files.delete(fileLogos.path(hash)); // A lost volume is recoverable; display never downloads.
        assertThat(logos.refresh(false).downloaded()).isEqualTo(16);
        assertThat(logoCache.contains(hash)).isTrue();
    }

    @Test void logoRefreshCannotOverlapAnotherInstanceOrMatchSync() throws Exception {
        sync.sync(FROM, TO); prepareLogos();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.fetchTeams(anyString())).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); return List.of();
        });
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = tasks.submit(() -> logos.refresh(false));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> logos.refresh(false)).isInstanceOf(DomainException.class);
            assertThatThrownBy(() -> sync.sync(FROM, TO)).isInstanceOf(DomainException.class);
            release.countDown(); assertThat(first.get(20, TimeUnit.SECONDS).missing()).isEqualTo(16);
        } finally { release.countDown(); }
    }

    @Test void logoRefreshRequiresAdminAndCsrfAndReadEndpointNeverCallsProvider() throws Exception {
        sync.sync(FROM, TO); prepareLogos();
        try (var guest = new TestHttpSession(port, json); var user = login()) {
            assertThat(guest.request("POST", "/admin/sportstats/logos", null, guest.csrf()).statusCode()).isEqualTo(401);
            assertThat(user.request("POST", "/admin/sportstats/logos", null, user.csrf()).statusCode()).isEqualTo(403);
            assertThat(guest.request("GET", "/teams/" + id("teams", "t1nc") + "/logo", null, null).statusCode()).isEqualTo(404);
        }
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", userId);
        try (var admin = login()) {
            assertThat(admin.request("POST", "/admin/sportstats/logos", null, null).statusCode()).isEqualTo(403);
            assertThat(admin.request("POST", "/admin/sportstats/logos", null, admin.csrf()).statusCode()).isEqualTo(200);
        }
        clearInvocations(client);
        try (var guest = new TestHttpSession(port, json)) {
            var image = guest.request("GET", "/teams/" + id("teams", "t1nc") + "/logo", null, null);
            assertThat(image.statusCode()).isEqualTo(200);
            assertThat(image.headers().firstValue("Content-Type")).contains("image/png");
            assertThat(image.headers().firstValue("ETag")).isPresent();
            assertThat(guest.request("GET", "/teams?size=100", null, null).body()).doesNotContain("sportstatsapi.com", "offline-secret");
        }
        verifyNoInteractions(client);
    }

    private void prepareLogos() throws Exception {
        var catalog = new ArrayList<JsonNode>();
        for (var row : jdbc.queryForList("SELECT external_id FROM teams WHERE provider='SPORTSTATS' ORDER BY external_id")) {
            String code = (String) row.get("external_id");
            // The display name intentionally does not match the imported team: only identity may join them.
            catalog.add(json.readTree(json.writeValueAsString(Map.of("code", code, "name", "Different name", "logo_credit", "Credit " + code))));
        }
        when(client.fetchTeams("l60")).thenReturn(catalog);
        doAnswer(call -> {
            JsonNode row = call.getArgument(0); String code = row.path("code").asText("");
            return new SportStatsClient.LogoSource(java.net.URI.create("https://img.sportstatsapi.com/logos/soccer/" + code + "-123.png"), null, "Credit " + code);
        }).when(client).logoSource(any());
        when(client.downloadLogo(any())).thenReturn(new SportStatsClient.Image(TeamLogoTests.png(16, 20), "image/png"));
    }

    private UUID id(String table,String external) { return jdbc.queryForObject("SELECT id FROM "+table+" WHERE provider='SPORTSTATS' AND external_id=?",UUID.class,external); }
    private ObjectNode changed(String code) { return ((ObjectNode)data.stream().filter(row -> code.equals(row.get("code").asText())).findFirst().orElseThrow()).deepCopy().put("updated_at","2026-11-24T12:00:00Z"); }
    private void batch(List<JsonNode> rows) { doReturn(new SportStatsClient.Batch(rows,1)).when(client).fetch(any(),any()); }
    private TestHttpSession login() throws Exception {
        var session = new TestHttpSession(port,json);
        var response = session.request("POST","/auth/login",Map.of("email",EMAIL,"password",PASSWORD),session.csrf());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200); return session;
    }
    private long points(TestHttpSession session) throws Exception {
        var rows = json.readTree(session.request("GET","/leaderboard?size=100&competitionId="+id("competitions","l60")+"&seasonId="+id("seasons","2026/2027"),null,null).body()).get("items");
        for (var row : rows) if (row.get("userId").asText().equals(userId.toString())) return row.get("totalPoints").asLong();
        throw new AssertionError("Missing test participant");
    }
}

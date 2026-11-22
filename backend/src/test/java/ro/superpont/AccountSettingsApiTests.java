package ro.superpont;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.security.AuthRateLimiter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:settings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.placeholders.leaderboardDemo=true"
})
@ActiveProfiles({"dev", "test"})
class AccountSettingsApiTests {
    private static final String SETTINGS = "/users/me/settings";
    private static final String HIDDEN = "20000000-0000-0000-0000-000000000006";
    private static final String PUBLIC = "20000000-0000-0000-0000-000000000007";
    private static final String FUTURE = "20000000-0000-0000-0000-000000000044";
    private static final String STARTED = "20000000-0000-0000-0000-000000000042";
    private static final String PASSWORD = "SuperPontDemo2026!";

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @MockitoBean Clock clock;
    @MockitoBean AuthRateLimiter rateLimiter;
    @MockitoBean ro.superpont.auth.service.VerificationEmailSender verificationEmail;

    @BeforeEach
    void reset() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T12:00:00Z"));
        jdbc.update("UPDATE users SET hide_predictions_until_kickoff = TRUE WHERE id = ?", UUID.fromString(HIDDEN));
        jdbc.update("UPDATE users SET hide_predictions_until_kickoff = FALSE WHERE id = ?", UUID.fromString(PUBLIC));
    }

    @Test
    void settingsRequireSessionAndCsrfAndOnlyAffectTheAuthenticatedUser() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            assertThat(session.request("GET", SETTINGS, null, null).statusCode()).isEqualTo(401);
            assertThat(session.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", false), session.csrf())
                    .statusCode()).isEqualTo(401);
            login(session, "demo_ascuns@leaderboard.example.test");
            var current = session.request("GET", SETTINGS, null, null);
            assertThat(current.statusCode()).isEqualTo(200);
            assertThat(current.headers().firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("no-store"));
            assertThat(json.readTree(current.body()).get("hidePredictionsUntilKickoff").asBoolean()).isTrue();
            assertThat(session.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", false), null).statusCode()).isEqualTo(403);
            assertThat(session.request("PUT", SETTINGS, Map.of(), session.csrf()).statusCode()).isEqualTo(400);
            assertThat(session.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", "false"), session.csrf()).statusCode()).isEqualTo(400);
            assertThat(session.request("PUT", SETTINGS, json.readTree("{\"hidePredictionsUntilKickoff\":null}"), session.csrf())
                    .statusCode()).isEqualTo(400);
            assertThat(session.request("PUT", "/users/" + PUBLIC + "/settings", Map.of("hidePredictionsUntilKickoff", true), session.csrf())
                    .statusCode()).isEqualTo(403);
            var updated = session.request("PUT", SETTINGS,
                    Map.of("hidePredictionsUntilKickoff", false, "userId", PUBLIC), session.csrf());
            assertThat(updated.statusCode()).isEqualTo(200);
            assertThat(json.readTree(updated.body()).get("hidePredictionsUntilKickoff").asBoolean()).isFalse();
            assertThat(preference(HIDDEN)).isFalse();
            assertThat(preference(PUBLIC)).isFalse();
        }
    }

    @Test
    void visibilityUsesTheOwnerPreferenceAndOwnersAlwaysSeeTheirScores() throws Exception {
        try (var otherAccount = new TestHttpSession(port, json);
                var hiddenOwner = new TestHttpSession(port, json);
                var publicOwner = new TestHttpSession(port, json)) {
            login(hiddenOwner, "demo_ascuns@leaderboard.example.test");
            login(publicOwner, "demo_public@leaderboard.example.test");
            login(otherAccount, "demo_ana@leaderboard.example.test");
            for (var viewer : java.util.List.of(otherAccount, publicOwner)) {
                var hidden = prediction(viewer, HIDDEN, FUTURE);
                assertThat(hidden.get("visibility").asText()).isEqualTo("HIDDEN");
                assertThat(hidden.has("predictedScore")).isFalse();
                assertThat(hidden.get("result").isNull()).isTrue();
            }
            assertRevealed(prediction(hiddenOwner, HIDDEN, FUTURE), 2, 0);
            for (var viewer : java.util.List.of(otherAccount, hiddenOwner, publicOwner)) {
                assertRevealed(prediction(viewer, PUBLIC, FUTURE), 3, 1);
                assertRevealed(prediction(viewer, HIDDEN, STARTED), 1, 2);
                assertRevealed(prediction(viewer, PUBLIC, STARTED), 2, 2);
            }
            var own = get(hiddenOwner, "/predictions/me");
            assertThat(own.get("items")).anySatisfy(item -> {
                assertThat(item.get("matchId").asText()).isEqualTo(FUTURE);
                assertThat(item.get("homeScore").asInt()).isEqualTo(2);
            });
            when(clock.instant()).thenReturn(Instant.parse("2027-06-20T18:00:00Z"));
            assertRevealed(prediction(otherAccount, HIDDEN, FUTURE), 2, 0);
            assertRevealed(prediction(otherAccount, PUBLIC, FUTURE), 3, 1);
            assertThat(hiddenOwner.request("PUT", "/matches/" + FUTURE + "/prediction",
                    Map.of("homeScore", 1, "awayScore", 0), hiddenOwner.csrf()).statusCode()).isEqualTo(409);
        }
    }

    @Test
    void changesAffectExistingAndNewPredictionsAndSurviveLoginAndSeedReruns() throws Exception {
        UUID newMatch = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO football_matches (id, season_id, round_id, home_team_id, away_team_id, kickoff, status)
                SELECT ?, season_id, round_id, away_team_id, home_team_id, kickoff, status
                FROM football_matches WHERE id = ?
                """, newMatch, UUID.fromString(FUTURE));
        try (var owner = new TestHttpSession(port, json); var viewer = new TestHttpSession(port, json)) {
            login(owner, "demo_ascuns@leaderboard.example.test");
            login(viewer, "demo_public@leaderboard.example.test");
            assertThat(prediction(viewer, HIDDEN, FUTURE).has("predictedScore")).isFalse();
            assertThat(owner.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", false), owner.csrf()).statusCode()).isEqualTo(200);
            assertRevealed(prediction(viewer, HIDDEN, FUTURE), 2, 0);
            var cached = viewer.request("GET", publicPath(HIDDEN), null, null,
                    Map.of("If-None-Match", "\"old-response\"", "If-Modified-Since", "Wed, 18 Nov 2026 12:00:00 GMT"));
            assertThat(cached.statusCode()).isEqualTo(200);
            assertThat(cached.headers().firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("no-store"));
            assertThat(cached.headers().firstValue("ETag")).isEmpty();
            assertThat(owner.request("PUT", "/matches/" + newMatch + "/prediction", Map.of("homeScore", 4, "awayScore", 2), owner.csrf())
                    .statusCode()).isEqualTo(201);
            assertRevealed(prediction(viewer, HIDDEN, newMatch.toString()), 4, 2);
            assertThat(owner.request("POST", "/auth/logout", null, owner.csrf()).statusCode()).isEqualTo(204);
            login(owner, "demo_ascuns@leaderboard.example.test");
            assertThat(get(owner, SETTINGS).get("hidePredictionsUntilKickoff").asBoolean()).isFalse();
            var sql = new ClassPathResource("db/devdata/R__superliga_leaderboard_demo.sql")
                    .getContentAsString(UTF_8).replace("${leaderboardDemo}", "true");
            var seed = new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(UTF_8)));
            seed.execute(flyway.getConfiguration().getDataSource());
            seed.execute(flyway.getConfiguration().getDataSource());
            assertThat(preference(HIDDEN)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(45);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM predictions", Long.class)).isEqualTo(59);
            assertThat(owner.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", true), owner.csrf()).statusCode()).isEqualTo(200);
            assertThat(prediction(viewer, HIDDEN, FUTURE).has("predictedScore")).isFalse();
            assertThat(prediction(viewer, HIDDEN, newMatch.toString()).has("predictedScore")).isFalse();
            assertRevealed(prediction(owner, HIDDEN, newMatch.toString()), 4, 2);
        } finally {
            jdbc.update("DELETE FROM predictions WHERE match_id = ?", newMatch);
            jdbc.update("DELETE FROM football_matches WHERE id = ?", newMatch);
        }
    }

    @Test
    void registeredAccountsDefaultToRevealingPredictions() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            var registered = session.request("POST", "/auth/register", Map.of("displayName", "settings_new",
                    "email", "settings_new@example.test", "password", PASSWORD), session.csrf());
            assertThat(registered.statusCode()).isEqualTo(200);
            jdbc.update("UPDATE users SET email_verified = TRUE WHERE email = 'settings_new@example.test'");
            try {
                login(session, "settings_new");
                assertThat(get(session, SETTINGS).get("hidePredictionsUntilKickoff").asBoolean()).isFalse();
            } finally {
                jdbc.update("DELETE FROM users WHERE email = 'settings_new@example.test'");
            }
        }
    }

    @Test
    void bothTogglePositionsPersistAndControlOnlyOtherAccountsBeforeEachKickoff() throws Exception {
        var kickoff = Instant.parse("2027-06-20T18:00:00Z");
        var scope = "?competitionId=10000000-0000-0000-0000-000000000002&seasonId=10000000-0000-0000-0000-000000000003";
        var selectedPath = "/leaderboard/users/" + HIDDEN + "/predictions" + scope;
        try (var owner = new TestHttpSession(port, json); var viewer = new TestHttpSession(port, json)) {
            login(owner, "demo_ascuns@leaderboard.example.test");
            login(viewer, "demo_public@leaderboard.example.test");
            for (boolean hide : new boolean[] {true, false, true}) {
                when(clock.instant()).thenReturn(kickoff.minusNanos(1));
                var saved = owner.request("PUT", SETTINGS, Map.of("hidePredictionsUntilKickoff", hide), owner.csrf());
                assertThat(saved.statusCode()).isEqualTo(200);
                assertThat(json.readTree(saved.body()).get("hidePredictionsUntilKickoff").asBoolean()).isEqualTo(hide);
                assertThat(get(owner, SETTINGS).get("hidePredictionsUntilKickoff").asBoolean()).isEqualTo(hide);
                try (var refreshed = new TestHttpSession(port, json)) {
                    login(refreshed, "demo_ascuns@leaderboard.example.test");
                    assertThat(get(refreshed, SETTINGS).get("hidePredictionsUntilKickoff").asBoolean()).isEqualTo(hide);
                }
                for (var path : java.util.List.of(publicPath(HIDDEN), selectedPath)) {
                    var otherView = predictionFrom(get(viewer, path), FUTURE);
                    assertThat(otherView.get("visibility").asText()).isEqualTo(hide ? "HIDDEN" : "REVEALED");
                    assertThat(otherView.has("predictedScore")).isEqualTo(!hide);
                    if (!hide) assertRevealed(otherView, 2, 0);
                    else assertThat(otherView.get("result").isNull()).isTrue();
                    assertRevealed(predictionFrom(get(owner, path), FUTURE), 2, 0);
                    assertRevealed(predictionFrom(get(viewer, path), STARTED), 1, 2);
                }
                // The viewer's own OFF setting does not expose an owner's hidden predictions.
                assertThat(get(viewer, SETTINGS).get("hidePredictionsUntilKickoff").asBoolean()).isFalse();
                when(clock.instant()).thenReturn(kickoff);
                assertRevealed(prediction(viewer, HIDDEN, FUTURE), 2, 0);
                assertRevealed(predictionFrom(get(viewer, selectedPath), FUTURE), 2, 0);
            }
        }
    }

    private boolean preference(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT hide_predictions_until_kickoff FROM users WHERE id = ?",
                Boolean.class, UUID.fromString(userId)));
    }

    private void login(TestHttpSession session, String email) throws Exception {
        var response = session.request("POST", "/auth/login", Map.of("email", email.contains("@") ? email : email + "@example.test", "password", PASSWORD), session.csrf());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private JsonNode get(TestHttpSession session, String path) throws Exception {
        var response = session.request("GET", path, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private String publicPath(String owner) { return "/leaderboard/superliga/users/" + owner + "/predictions"; }

    private JsonNode prediction(TestHttpSession session, String owner, String match) throws Exception {
        return predictionFrom(get(session, publicPath(owner)), match);
    }

    private JsonNode predictionFrom(JsonNode ticket, String match) {
        for (var item : ticket.at("/predictions/items")) {
            if (item.get("matchId").asText().equals(match)) return item;
        }
        throw new AssertionError("Missing prediction for " + match);
    }

    private void assertRevealed(JsonNode prediction, int home, int away) {
        assertThat(prediction.get("visibility").asText()).isEqualTo("REVEALED");
        assertThat(prediction.at("/predictedScore/home").asInt()).isEqualTo(home);
        assertThat(prediction.at("/predictedScore/away").asInt()).isEqualTo(away);
    }
}

package ro.superpont.football;

import static org.assertj.core.api.Assertions.*;

import ro.superpont.TestHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.security.AuthRateLimiter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FootballApiTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AuthRateLimiter rateLimiter;
    TestHttpSession session;
    String csrf;
    String country, competition, season, phase, round, home, away;

    record Response(int status, JsonNode body, String location) {}

    Response request(String method, String path, Object payload) throws Exception {
        var response = session.request(method, path, payload, csrf);
        return new Response(
                response.statusCode(),
                mapper.readTree(response.body()),
                response.headers().firstValue("Location").orElse(""));
    }


    String create(String path, Object body) throws Exception {
        var response = request("POST", path, body);
        assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
        String id = response.body().get("id").asText();
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(response.location()).endsWith(id);
        return id;
    }

    String createSeason(String competitionId, String name) throws Exception {
        return create(
                "/seasons",
                Map.of(
                        "competitionId",
                        competitionId,
                        "name",
                        name,
                        "startDate",
                        "2026-07-01",
                        "endDate",
                        "2027-06-30"));
    }

    String createPhase(String seasonId, String code, int position, String format) throws Exception {
        return create(
                "/seasons/" + seasonId + "/phases",
                Map.of(
                        "code",
                        code,
                        "name",
                        code,
                        "format",
                        format,
                        "position",
                        position,
                        "winPoints",
                        5,
                        "drawPoints",
                        2,
                        "lossPoints",
                        0));
    }

    Map<String, Object> match() {
        return new HashMap<>(
                Map.of(
                        "roundId",
                        round,
                        "homeTeamId",
                        home,
                        "awayTeamId",
                        away,
                        "kickoff",
                        "2026-11-06T18:00:00+02:00",
                        "status",
                        "SCHEDULED",
                        "venue",
                        "Test ground"));
    }

    @BeforeEach
    void setUp() throws Exception {
        int n = IDS.getAndIncrement();
        String admin = "football-admin-" + n;
        String password = "test-admin-password";
        jdbc.update("""
                INSERT INTO users (id, username, email, password_hash, role, created_at)
                VALUES (?, ?, ?, ?, 'ADMIN', CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), admin, admin + "@example.test", passwords.encode(password));
        session = new TestHttpSession(port, mapper);
        var login = session.request("POST", "/auth/login",
                Map.of("username", admin, "password", password), session.csrf());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        csrf = session.csrf();
        String iso = "" + (char) ('A' + n / 26) + (char) ('A' + n % 26);
        country =
                create(
                        "/countries",
                        Map.of(
                                "name",
                                "Test country " + n,
                                "isoCode",
                                iso.toLowerCase(Locale.ROOT)));
        competition =
                create(
                        "/competitions",
                        Map.of("countryId", country, "code", "LEAGUE", "name", "Test league"));
        season = createSeason(competition, "2026/27");
        phase = createPhase(season, "CUSTOM_GROUP", 1, "LEAGUE");
        round = create("/rounds", Map.of("phaseId", phase, "number", 1, "name", "Opening round"));
        home = create("/teams", Map.of("countryId", country, "name", "Home", "shortName", "H"));
        away = create("/teams", Map.of("countryId", country, "name", "Away", "shortName", "A"));
        assertThat(
                        request("POST", "/seasons/" + season + "/teams", Map.of("teamId", home))
                                .status())
                .isEqualTo(200);
        assertThat(
                        request("POST", "/seasons/" + season + "/teams", Map.of("teamId", away))
                                .status())
                .isEqualTo(200);
    }

    @AfterEach
    void closeSession() {
        if (session != null) session.close();
    }

    @Test
    void migrationsRunWithoutDevelopmentSeeds() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM competitions WHERE code = 'RO-SUPERLIGA'",
                                Long.class))
                .isZero();
    }

    @Test
    void createsAndReadsTheEntireHierarchyWithUuidIdsAndFilters() throws Exception {
        String id = create("/matches", match());
        var fixture = request("GET", "/matches/" + id, null);
        assertThat(fixture.status()).isEqualTo(200);
        assertThat(fixture.body().get("seasonId").asText()).isEqualTo(season);
        assertThat(fixture.body().get("homeTeam").get("id").asText()).isEqualTo(home);
        assertThat(
                        request("GET", "/competitions?countryId=" + country, null)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        request("GET", "/seasons?competitionId=" + competition, null)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        request("GET", "/rounds?seasonId=" + season, null)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        request("GET", "/matches?seasonId=" + season + "&roundId=" + round, null)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        request("GET", "/teams?countryId=" + country, null)
                                .body()
                                .get("totalElements")
                                .asInt())
                .isEqualTo(2);
        for (var path :
                List.of(
                        "/countries/" + country,
                        "/competitions/" + competition,
                        "/teams/" + home,
                        "/seasons/" + season,
                        "/phases/" + phase,
                        "/rounds/" + round)) {
            assertThat(request("GET", path, null).status()).isEqualTo(200);
        }
    }

    @Test
    void sameTeamCanEnterAnotherCompetitionAndAnotherSeason() throws Exception {
        String cup =
                create(
                        "/competitions",
                        Map.of("countryId", country, "code", "CUP", "name", "Other competition"));
        String cupSeason = createSeason(cup, "2026/27");
        String secondSeason = createSeason(competition, "Another season");
        for (String id : List.of(cupSeason, secondSeason, season)) {
            assertThat(
                            request("POST", "/seasons/" + id + "/teams", Map.of("teamId", home))
                                    .status())
                    .isEqualTo(200);
        }
        assertThat(request("GET", "/seasons/" + cupSeason + "/teams", null).body().size())
                .isEqualTo(1);
        assertThat(request("GET", "/seasons/" + season + "/teams", null).body().size())
                .isEqualTo(2);
    }

    @Test
    void phasesAreConfigurableAndRoundNumbersAreScopedToTheirPhase() throws Exception {
        String finalPhase = createPhase(season, "CUSTOM_FINAL", 2, "KNOCKOUT");
        create("/rounds", Map.of("phaseId", finalPhase, "number", 1, "name", "Final"));
        var list = request("GET", "/seasons/" + season + "/phases", null).body();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).get("winPoints").asInt()).isEqualTo(5);
        assertThat(list.get(1).get("format").asText()).isEqualTo("KNOCKOUT");
        assertThat(
                        request(
                                        "POST",
                                        "/rounds",
                                        Map.of("phaseId", phase, "number", 1, "name", "Duplicate"))
                                .status())
                .isEqualTo(409);
    }

    @Test
    void countryCodesAreNormalizedAndDuplicatesReturnConflict() throws Exception {
        var existing = request("GET", "/countries/" + country, null).body();
        String code = existing.get("isoCode").asText();
        assertThat(code).isUpperCase();
        assertThat(
                        request(
                                        "POST",
                                        "/countries",
                                        Map.of(
                                                "name",
                                                "Duplicate",
                                                "isoCode",
                                                code.toLowerCase(Locale.ROOT)))
                                .status())
                .isEqualTo(409);
    }

    @Test
    void rejectsInvalidFieldsDatesAndPagination() throws Exception {
        var blank =
                request(
                        "POST",
                        "/teams",
                        Map.of("countryId", country, "name", " ", "shortName", "A"));
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.body().get("errors").has("name")).isTrue();
        assertThat(request("POST", "/countries", Map.of("name", "X", "isoCode", "ROM")).status())
                .isEqualTo(400);
        assertThat(
                        request(
                                        "POST",
                                        "/seasons",
                                        Map.of(
                                                "competitionId",
                                                competition,
                                                "name",
                                                "Bad",
                                                "startDate",
                                                "2027-01-01",
                                                "endDate",
                                                "2026-01-01"))
                                .status())
                .isEqualTo(400);
        assertThat(request("GET", "/matches?size=101", null).status()).isEqualTo(400);
        assertThat(request("GET", "/countries?page=-1", null).status()).isEqualTo(400);
        assertThat(request("GET", "/teams/not-a-uuid", null).status()).isEqualTo(400);
        assertThat(request("GET", "/teams/" + UUID.randomUUID(), null).status()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidPhaseConfigurationAndUnknownRelationships() throws Exception {
        assertThat(
                        request(
                                        "POST",
                                        "/seasons/" + season + "/phases",
                                        Map.of(
                                                "code",
                                                "BAD",
                                                "name",
                                                "Bad",
                                                "format",
                                                "LEAGUE",
                                                "position",
                                                0,
                                                "winPoints",
                                                -1,
                                                "drawPoints",
                                                1,
                                                "lossPoints",
                                                0))
                                .status())
                .isEqualTo(400);
        assertThat(
                        request(
                                        "POST",
                                        "/rounds",
                                        Map.of(
                                                "phaseId",
                                                UUID.randomUUID(),
                                                "number",
                                                1,
                                                "name",
                                                "X"))
                                .status())
                .isEqualTo(404);
        assertThat(
                        request(
                                        "POST",
                                        "/competitions",
                                        Map.of(
                                                "countryId",
                                                UUID.randomUUID(),
                                                "code",
                                                "X",
                                                "name",
                                                "Unknown"))
                                .status())
                .isEqualTo(404);
    }

    @Test
    void rejectsSelfMatchesAndTeamsFromAnotherSeason() throws Exception {
        var self = match();
        self.put("awayTeamId", home);
        assertThat(request("POST", "/matches", self).status()).isEqualTo(400);
        String outsider =
                create(
                        "/teams",
                        Map.of("countryId", country, "name", "Outsider", "shortName", "O"));
        var unregistered = match();
        unregistered.put("awayTeamId", outsider);
        assertThat(request("POST", "/matches", unregistered).status()).isEqualTo(400);
    }

    @Test
    void rejectsKickoffOutsideSeasonAndMismatchedRoundSeasonFilters() throws Exception {
        var outside = match();
        outside.put("kickoff", "2030-01-01T00:00:00Z");
        assertThat(request("POST", "/matches", outside).status()).isEqualTo(400);
        String other = createSeason(competition, "Different");
        assertThat(
                        request("GET", "/matches?roundId=" + round + "&seasonId=" + other, null)
                                .status())
                .isEqualTo(400);
    }

    @Test
    void rejectsDuplicateFixturesAndInvalidResults() throws Exception {
        String id = create("/matches", match());
        assertThat(request("POST", "/matches", match()).status()).isEqualTo(409);
        for (var result :
                List.of(
                        Map.of("status", "FINISHED"),
                        Map.of("status", "FINISHED", "homeScore", 1),
                        Map.of("status", "SCHEDULED", "homeScore", 1, "awayScore", 0),
                        Map.of("status", "FINISHED", "homeScore", -1, "awayScore", 0),
                        Map.of("status", "FINISHED", "homeScore", 1.5, "awayScore", 0))) {
            assertThat(request("PUT", "/matches/" + id + "/result", result).status())
                    .as(result.toString())
                    .isEqualTo(400);
        }
        var result =
                request(
                        "PUT",
                        "/matches/" + id + "/result",
                        Map.of("status", "FINISHED", "homeScore", 0, "awayScore", 99));
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("awayScore").asInt()).isEqualTo(99);
        assertThat(
                        request(
                                        "PUT",
                                        "/matches/" + id + "/result",
                                        Map.of(
                                                "status",
                                                "FINISHED",
                                                "homeScore",
                                                100,
                                                "awayScore",
                                                0))
                                .status())
                .isEqualTo(200);
        assertThat(request("GET", "/matches/" + id, null).body().get("status").asText())
                .isEqualTo("FINISHED");
    }

    @Test
    void databaseAlsoEnforcesSeasonAndParticipationBoundaries() throws Exception {
        String otherSeason = createSeason(competition, "Other boundary season");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO rounds (id, season_id, phase_id, round_number,"
                                                + " name) VALUES (?, ?, ?, 2, 'Bad round')",
                                        UUID.randomUUID(),
                                        UUID.fromString(otherSeason),
                                        UUID.fromString(phase)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String outsider =
                create(
                        "/teams",
                        Map.of("countryId", country, "name", "Unregistered", "shortName", "U"));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO football_matches (id, season_id, round_id,"
                                            + " home_team_id, away_team_id, kickoff, status) VALUES"
                                            + " (?, ?, ?, ?, ?, ?, 'SCHEDULED')",
                                        UUID.randomUUID(),
                                        UUID.fromString(season),
                                        UUID.fromString(round),
                                        UUID.fromString(home),
                                        UUID.fromString(outsider),
                                        java.time.OffsetDateTime.parse("2026-11-06T16:00:00Z")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO football_matches (id, season_id, round_id,"
                                            + " home_team_id, away_team_id, kickoff, status) VALUES"
                                            + " (?, ?, ?, ?, ?, ?, 'SCHEDULED')",
                                        UUID.randomUUID(),
                                        UUID.fromString(otherSeason),
                                        UUID.fromString(round),
                                        UUID.fromString(home),
                                        UUID.fromString(away),
                                        java.time.OffsetDateTime.parse("2026-11-06T16:00:00Z")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}

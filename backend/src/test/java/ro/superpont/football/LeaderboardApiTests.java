package ro.superpont.football;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.TestHttpSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_LEADERBOARD_DATABASE_URL:jdbc:h2:mem:leaderboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.flyway.placeholders.leaderboardDemo=true"
})
@ActiveProfiles({"dev", "test"})
class LeaderboardApiTests {
    private static final String LEADERBOARD = "/leaderboard/superliga";
    private static final String FIRST_MATCH = "20000000-0000-0000-0000-000000000040";
    private static final String LIVE_MATCH = "20000000-0000-0000-0000-000000000042";
    private static final String HISTORICAL_MATCH = "20000000-0000-0000-0000-000000000043";
    private static final String FUTURE_MATCH = "20000000-0000-0000-0000-000000000044";
    private static final String ANA = "20000000-0000-0000-0000-000000000001";
    private static final String RADU = "20000000-0000-0000-0000-000000000002";

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PasswordEncoder passwords;
    @MockitoBean Clock clock;

    @BeforeEach
    void resetResult() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-17T12:00:00Z"));
        jdbc.update("UPDATE football_matches SET home_score = 5, away_score = 0 WHERE id = ?", UUID.fromString(FIRST_MATCH));
    }

    @Test
    void ranksOptInFixturesAndReflectsCorrectionsInTheTicketAndLeaderboard() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-17T12:00:00Z"));
        try (var publicSession = new TestHttpSession(port, json);
                var ana = new TestHttpSession(port, json);
                var admin = new TestHttpSession(port, json)) {
            assertThat(get(publicSession, LEADERBOARD).get("items"))
                    .extracting(this::standing)
                    .containsExactly(tuple(1, "Ana", 18), tuple(2, "Mara", 13),
                            tuple(2, "Radu", 13), tuple(4, "Vlad", 0));
            var secondPage = get(publicSession, LEADERBOARD + "?page=1&size=2");
            assertThat(secondPage.get("totalElements").asInt()).isEqualTo(4);
            assertThat(secondPage.get("items")).extracting(this::standing)
                    .containsExactly(tuple(2, "Radu", 13), tuple(4, "Vlad", 0));

            login(ana, "demo_ana", "SuperPontDemo2026!");
            assertThat(get(publicSession, LEADERBOARD).at("/items/0/userId"))
                    .isEqualTo(get(ana, "/users/me").get("id"));
            var ticket = get(ana, "/predictions/me");
            assertThat(result(ticket, FIRST_MATCH).get("points").asInt()).isEqualTo(10);
            assertThat(result(ticket, LIVE_MATCH).isNull()).isTrue();
            assertThat(result(ticket, HISTORICAL_MATCH).get("points").asInt()).isEqualTo(10);

            String adminPassword = "local-test-admin";
            jdbc.update("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at)
                    VALUES (?, 'leaderboard_admin', 'leaderboard_admin@example.test', ?, 'ADMIN', CURRENT_TIMESTAMP)
                    """, UUID.randomUUID(), passwords.encode(adminPassword));
            login(admin, "leaderboard_admin", adminPassword);
            var correction = admin.request("PUT", "/matches/" + FIRST_MATCH + "/result",
                    Map.of("status", "FINISHED", "homeScore", 4, "awayScore", 0), admin.csrf());
            assertThat(correction.statusCode()).as(correction.body()).isEqualTo(200);

            var seed = new ClassPathResource("db/devdata/R__superliga_leaderboard_demo.sql");
            var sql = seed.getContentAsString(UTF_8).replace("${leaderboardDemo}", "true");
            new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(UTF_8)))
                    .execute(flyway.getConfiguration().getDataSource());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM predictions", Long.class)).isEqualTo(13);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(5);

            var corrected = result(get(ana, "/predictions/me"), FIRST_MATCH);
            assertThat(corrected.get("homeScore").asInt()).isEqualTo(4);
            assertThat(corrected.get("awayScore").asInt()).isZero();
            assertThat(corrected.get("points").asInt()).isEqualTo(9);
            var publicTicket = get(publicSession, publicPath(ANA));
            assertThat(publicTicket.get("totalPoints").asInt()).isEqualTo(17);
            assertThat(publicPrediction(publicTicket, FIRST_MATCH).get("result")).isEqualTo(corrected);
            assertThat(get(publicSession, LEADERBOARD).get("items")).extracting(this::standing)
                    .containsExactly(tuple(1, "Ana", 17), tuple(2, "Mara", 14),
                            tuple(2, "Radu", 14), tuple(4, "Vlad", 1));
        }
    }

    @Test
    void publicScoresFollowTheEditingDeadlineForBothAccounts() throws Exception {
        var deadline = Instant.parse("2027-06-20T18:00:00Z");
        when(clock.instant()).thenReturn(deadline.minusNanos(1));
        try (var ana = new TestHttpSession(port, json);
                var radu = new TestHttpSession(port, json)) {
            login(ana, "demo_ana", "SuperPontDemo2026!");
            login(radu, "demo_radu", "SuperPontDemo2026!");
            for (var viewer : java.util.List.of(ana, radu)) {
                for (var selected : java.util.List.of(ANA, RADU)) {
                    var response = viewer.request("GET", publicPath(selected) + "?size=1", null, null);
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
                    var ticket = json.readTree(response.body());
                    assertThat(ticket.get("displayName").asText()).isIn("Ana", "Radu");
                    assertThat(response.body()).doesNotContain("email", "password", "createdAt", "updatedAt");
                    var future = publicPrediction(ticket, FUTURE_MATCH);
                    assertThat(future.get("visibility").asText()).isEqualTo("HIDDEN");
                    assertThat(future.has("predictedScore")).isFalse();
                    assertThat(future.get("result").isNull()).isTrue();
                    assertThat(response.body()).doesNotContain("homeScore", "awayScore");
                }
            }
            String csrf = ana.csrf();
            var saved = ana.request("PUT", "/matches/" + FUTURE_MATCH + "/prediction",
                    Map.of("homeScore", 3, "awayScore", 2), csrf);
            assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
            assertThat(result(get(ana, "/predictions/me"), FIRST_MATCH).get("points").asInt()).isEqualTo(10);

            when(clock.instant()).thenReturn(deadline);
            assertThat(ana.request("PUT", "/matches/" + FUTURE_MATCH + "/prediction",
                    Map.of("homeScore", 3, "awayScore", 2), csrf).statusCode()).isEqualTo(409);
            assertThat(ana.request("DELETE", "/matches/" + FUTURE_MATCH + "/prediction", null, csrf).statusCode()).isEqualTo(409);
            for (var viewer : java.util.List.of(ana, radu)) {
                var revealed = publicPrediction(get(viewer, publicPath(ANA)), FUTURE_MATCH);
                assertThat(revealed.get("visibility").asText()).isEqualTo("REVEALED");
                assertThat(revealed.at("/predictedScore/home").asInt()).isEqualTo(3);
                assertThat(revealed.at("/predictedScore/away").asInt()).isEqualTo(2);
                assertThat(revealed.get("result").isNull()).isTrue();
                var ticket = get(viewer, publicPath(ANA));
                assertThat(ticket.get("totalPoints").asInt()).isEqualTo(18);
                assertThat(publicPrediction(ticket, LIVE_MATCH).get("result").isNull()).isTrue();
                assertThat(publicPrediction(ticket, FIRST_MATCH).at("/result/points").asInt()).isEqualTo(10);
            }
            var zero = get(ana, publicPath("20000000-0000-0000-0000-000000000005"));
            assertThat(zero.get("totalPoints").asInt()).isZero();
            assertThat(publicPrediction(zero, FIRST_MATCH).at("/result/points").asInt()).isZero();
        }
    }

    @Test
    void excludesOtherSeasonsAndCompetitionsAndSupportsEmptyUsers() throws Exception {
        UUID otherCompetition = UUID.randomUUID();
        UUID otherSeason = UUID.randomUUID();
        UUID otherPhase = UUID.randomUUID();
        UUID otherRound = UUID.randomUUID();
        UUID otherMatch = UUID.randomUUID();
        UUID otherPrediction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO competitions (id, country_id, code, name)
                SELECT ?, country_id, 'OTHER-LEADERBOARD-TEST', 'Other competition' FROM competitions
                WHERE id = '10000000-0000-0000-0000-000000000002'
                """, otherCompetition);
        jdbc.update("""
                INSERT INTO seasons (id, competition_id, name, start_date, end_date)
                SELECT ?, ?, 'Other current season', start_date, end_date FROM seasons
                WHERE id = '10000000-0000-0000-0000-000000000003'
                """, otherSeason, otherCompetition);
        jdbc.update("""
                INSERT INTO season_teams (season_id, team_id)
                SELECT ?, id FROM teams
                WHERE id IN ('10000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000011')
                """, otherSeason);
        jdbc.update("""
                INSERT INTO competition_phases (id, season_id, code, name, format, position, win_points, draw_points, loss_points)
                VALUES (?, ?, 'GROUP', 'Group', 'LEAGUE', 1, 3, 1, 0)
                """, otherPhase, otherSeason);
        jdbc.update("""
                INSERT INTO rounds (id, season_id, phase_id, round_number, name) VALUES (?, ?, ?, 50, 'Other round')
                """, otherRound, otherSeason, otherPhase);
        jdbc.update("""
                INSERT INTO football_matches (id, season_id, round_id, home_team_id, away_team_id, kickoff, status, home_score, away_score)
                SELECT ?, ?, ?, home_team_id, away_team_id, kickoff, status, home_score, away_score
                FROM football_matches WHERE id = ?
                """, otherMatch, otherSeason, otherRound, UUID.fromString(FIRST_MATCH));
        jdbc.update("""
                INSERT INTO predictions (id, user_id, match_id, home_score, away_score, created_at, updated_at)
                VALUES (?, ?, ?, 5, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, otherPrediction, UUID.fromString(ANA), otherMatch);
        try (var session = new TestHttpSession(port, json)) {
            var ticket = get(session, publicPath(ANA));
            assertThat(ticket.get("displayName").asText()).isEqualTo("Ana");
            assertThat(ticket.get("competitionName").asText()).isEqualTo("SuperLiga");
            assertThat(ticket.get("totalPoints").asInt()).isEqualTo(18);
            assertThat(ticket.at("/predictions/totalElements").asInt()).isEqualTo(4);
            assertThat(ticket.at("/predictions/items")).extracting(row -> row.get("roundNumber").asInt())
                    .containsExactly(18, 17, 17, 17);
            assertThat(ticket.toString()).doesNotContain(otherMatch.toString(), HISTORICAL_MATCH);

            UUID emptyUser = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at)
                    VALUES (?, 'empty_leaderboard_user', 'empty@example.test', ?, 'USER', CURRENT_TIMESTAMP)
                    """, emptyUser, passwords.encode("empty-password"));
            try {
                var empty = get(session, publicPath(emptyUser.toString()));
                assertThat(empty.get("displayName").asText()).isEqualTo("empty_leaderboard_user");
                assertThat(empty.get("totalPoints").asInt()).isZero();
                assertThat(empty.at("/predictions/totalElements").asInt()).isZero();
            } finally {
                jdbc.update("DELETE FROM users WHERE id = ?", emptyUser);
            }
            assertThat(session.request("GET", publicPath(UUID.randomUUID().toString()), null, null).statusCode()).isEqualTo(404);
            assertThat(session.request("GET", publicPath(ANA) + "?size=101", null, null).statusCode()).isEqualTo(400);
        } finally {
            jdbc.update("DELETE FROM predictions WHERE id = ?", otherPrediction);
            jdbc.update("DELETE FROM football_matches WHERE id = ?", otherMatch);
            jdbc.update("DELETE FROM rounds WHERE id = ?", otherRound);
            jdbc.update("DELETE FROM competition_phases WHERE id = ?", otherPhase);
            jdbc.update("DELETE FROM season_teams WHERE season_id = ?", otherSeason);
            jdbc.update("DELETE FROM seasons WHERE id = ?", otherSeason);
            jdbc.update("DELETE FROM competitions WHERE id = ?", otherCompetition);
        }
    }

    @Test
    void returnsAnEmptyPageWhenThereIsNoCurrentSeason() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2027-07-01T00:00:00Z"));
        try (var session = new TestHttpSession(port, json)) {
            assertThat(get(session, LEADERBOARD).get("totalElements").asInt()).isZero();
            assertThat(session.request("GET", LEADERBOARD + "?size=101", null, null).statusCode()).isEqualTo(400);
            assertThat(session.request("GET", publicPath(ANA), null, null).statusCode()).isEqualTo(404);
        }
    }

    private JsonNode get(TestHttpSession session, String path) throws Exception {
        var response = session.request("GET", path, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private void login(TestHttpSession session, String username, String password) throws Exception {
        var response = session.request("POST", "/auth/login",
                Map.of("username", username, "password", password), session.csrf());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private Tuple standing(JsonNode row) {
        return tuple(row.get("rank").asInt(), row.get("displayName").asText(), row.get("totalPoints").asInt());
    }

    private JsonNode result(JsonNode ticket, String matchId) {
        for (var prediction : ticket.get("items")) {
            if (matchId.equals(prediction.get("matchId").asText())) return prediction.get("result");
        }
        throw new AssertionError("No prediction for " + matchId);
    }

    private String publicPath(String userId) {
        return LEADERBOARD + "/users/" + userId + "/predictions";
    }

    private JsonNode publicPrediction(JsonNode ticket, String matchId) {
        for (var prediction : ticket.at("/predictions/items")) {
            if (matchId.equals(prediction.get("matchId").asText())) return prediction;
        }
        throw new AssertionError("No public prediction for " + matchId);
    }
}

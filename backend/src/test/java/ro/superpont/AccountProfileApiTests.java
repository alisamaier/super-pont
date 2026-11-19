package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.security.AuthRateLimiter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:account_profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.placeholders.leaderboardDemo=true"
})
@ActiveProfiles({"dev", "test"})
class AccountProfileApiTests {
    private static final String OLD_PASSWORD = "SuperPontDemo2026!";
    private static final String NEW_PASSWORD = "ParolaNoua2026!";
    private static final String ANA = "20000000-0000-0000-0000-000000000001";
    private static final String RADU = "20000000-0000-0000-0000-000000000002";
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AuthRateLimiter rateLimiter;
    @MockitoBean Clock clock;

    @BeforeEach
    void reset() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-19T12:00:00Z"));
        jdbc.update("UPDATE users SET password_hash = ?, password_version = password_version + 1, display_name = 'Ana' WHERE id = ?",
                passwords.encode(OLD_PASSWORD), UUID.fromString(ANA));
    }

    @Test
    void profileIsTrimmedValidatedAndVisibleThroughoutTheApplicationAndNewLogins() throws Exception {
        try (var owner = new TestHttpSession(port, json); var other = new TestHttpSession(port, json)) {
            login(owner, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            login(other, "demo_radu@leaderboard.example.test", OLD_PASSWORD, 200);
            for (String invalid : java.util.List.of("", " \t\n ", "a".repeat(65))) {
                assertThat(owner.request("PUT", "/users/me/profile", Map.of("displayName", invalid), owner.csrf()).statusCode()).isEqualTo(400);
            }
            assertThat(owner.request("PUT", "/users/me/profile", Map.of("displayName", "Alt nume"), null).statusCode()).isEqualTo(403);
            var saved = owner.request("PUT", "/users/me/profile", Map.of("displayName", "  Ana Ștefan  ", "userId", RADU), owner.csrf());
            assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
            assertThat(json.readTree(saved.body()).get("displayName").asText()).isEqualTo("Ana Ștefan");
            assertThat(json.readTree(saved.body()).get("email").asText()).isEqualTo("demo_ana@leaderboard.example.test");
            assertThat(saved.body()).doesNotContain("passwordHash", "passwordVersion");
            assertThat(get(other, "/users/me").get("displayName").asText()).isEqualTo("Radu");
            assertThat(get(other, "/leaderboard/superliga").get("items")).anySatisfy(row -> {
                assertThat(row.get("userId").asText()).isEqualTo(ANA);
                assertThat(row.get("displayName").asText()).isEqualTo("Ana Ștefan");
            });
            assertThat(get(other, "/leaderboard/superliga/users/" + ANA + "/predictions").get("displayName").asText()).isEqualTo("Ana Ștefan");
            assertThat(owner.request("POST", "/auth/logout", null, owner.csrf()).statusCode()).isEqualTo(204);
            login(owner, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            assertThat(get(owner, "/users/me").get("displayName").asText()).isEqualTo("Ana Ștefan");
            // The existing schema permits duplicate display names; email addresses remain unique.
            assertThat(owner.request("PUT", "/users/me/profile", Map.of("displayName", "Radu"), owner.csrf()).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void invalidPasswordChangesDoNotChangeCredentialsOrInvalidateSessions() throws Exception {
        try (var owner = new TestHttpSession(port, json); var second = new TestHttpSession(port, json)) {
            login(owner, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            login(second, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            assertThat(owner.request("PUT", "/users/me/password", Map.of("currentPassword", "wrong password", "newPassword", NEW_PASSWORD), owner.csrf())
                    .statusCode()).isEqualTo(400);
            for (String invalid : java.util.List.of("", "short", " ".repeat(8), "x".repeat(73), "ă".repeat(37))) {
                assertThat(owner.request("PUT", "/users/me/password", Map.of("currentPassword", OLD_PASSWORD, "newPassword", invalid), owner.csrf())
                        .statusCode()).isEqualTo(400);
            }
            assertThat(owner.request("PUT", "/users/me/password", Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD), null)
                    .statusCode()).isEqualTo(403);
            assertThat(get(second, "/users/me").get("email").asText()).isEqualTo("demo_ana@leaderboard.example.test");
            assertThat(passwords.matches(OLD_PASSWORD, hash())).isTrue();
        }
    }

    @Test
    void passwordChangeRevokesAllExistingSessionsAndRequiresTheNewPassword() throws Exception {
        try (var owner = new TestHttpSession(port, json); var second = new TestHttpSession(port, json);
                var third = new TestHttpSession(port, json); var otherUser = new TestHttpSession(port, json)) {
            login(owner, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            login(second, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            login(third, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 200);
            login(otherUser, "demo_radu@leaderboard.example.test", OLD_PASSWORD, 200);
            String secondCsrf = second.csrf();
            var changed = owner.request("PUT", "/users/me/password",
                    Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD, "userId", RADU), owner.csrf());
            assertThat(changed.statusCode()).as(changed.body()).isEqualTo(204);
            assertThat(changed.headers().allValues("Set-Cookie")).anySatisfy(cookie -> assertThat(cookie).startsWith("JSESSIONID=;").contains("1970"));
            assertThat(hash()).isNotEqualTo(NEW_PASSWORD);
            assertThat(passwords.matches(NEW_PASSWORD, hash())).isTrue();
            assertThat(passwords.matches(OLD_PASSWORD, hash())).isFalse();
            assertThat(owner.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            var revoked = second.request("GET", "/leaderboard/superliga", null, null,
                    Map.of("Origin", "https://frontend.example.test"));
            assertThat(revoked.statusCode()).isEqualTo(401);
            assertThat(revoked.headers().firstValue("Access-Control-Allow-Origin")).contains("https://frontend.example.test");
            assertThat(second.request("PUT", "/users/me/profile", Map.of("displayName", "Unauthorized"), secondCsrf).statusCode()).isIn(401, 403);
            assertThat(third.request("GET", "/users/me/settings", null, null).statusCode()).isEqualTo(401);
            assertThat(get(otherUser, "/users/me").get("email").asText()).isEqualTo("demo_radu@leaderboard.example.test");
            login(owner, "demo_ana@leaderboard.example.test", OLD_PASSWORD, 401);
            login(owner, "demo_ana@leaderboard.example.test", NEW_PASSWORD, 200);
            assertThat(get(owner, "/users/me").get("email").asText()).isEqualTo("demo_ana@leaderboard.example.test");
        }
    }

    @Test
    void accountOperationsUseOnlyTheAuthenticatedIdentity() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            assertThat(session.request("PUT", "/users/me/profile", Map.of("displayName", "Name"), session.csrf()).statusCode()).isEqualTo(401);
            assertThat(session.request("PUT", "/users/me/password", Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD), session.csrf())
                    .statusCode()).isEqualTo(401);
            login(session, "demo_radu@leaderboard.example.test", OLD_PASSWORD, 200);
            assertThat(session.request("PUT", "/users/" + ANA + "/profile", Map.of("displayName", "Intruder"), session.csrf()).statusCode()).isEqualTo(403);
            assertThat(session.request("PUT", "/users/" + ANA + "/password", Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD), session.csrf())
                    .statusCode()).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, UUID.fromString(ANA))).isEqualTo("Ana");
            assertThat(passwords.matches(OLD_PASSWORD, hash())).isTrue();
        }
    }

    private String hash() { return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, UUID.fromString(ANA)); }

    private void login(TestHttpSession session, String email, String password, int status) throws Exception {
        var response = session.request("POST", "/auth/login", Map.of("email", email.contains("@") ? email : email + "@example.test", "password", password), session.csrf());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }

    private JsonNode get(TestHttpSession session, String path) throws Exception {
        var response = session.request("GET", path, null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
}

package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.model.User;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.auth.security.*;
import ro.superpont.auth.service.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_RESET_DATABASE_URL:jdbc:h2:mem:password_reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
    "spring.flyway.locations=classpath:db/migration,classpath:db/devdata",
    "spring.flyway.placeholders.leaderboardDemo=false",
    "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false"
})
class PasswordResetApiTests {
    private static final String EMAIL = "recovery@example.test", OLD = "Old-password-2026!", NEW = "New-password-2026!";
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean Clock clock;
    @MockitoBean AuthRateLimiter limiter;
    @MockitoBean PasswordResetEmailSender mail;
    @MockitoBean VerificationEmailSender verificationMail;
    private UUID userId;
    private List<String> delivered;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("DELETE FROM predictions");
        jdbc.update("DELETE FROM users");
        var user = new User("Recuperare", EMAIL, passwords.encode(OLD));
        user.verifyEmail();
        user.setHidePredictionsUntilKickoff(true);
        userId = users.saveAndFlush(user).getId();
        jdbc.update("""
                INSERT INTO predictions (id,user_id,match_id,home_score,away_score,created_at,updated_at)
                SELECT ?,?,id,2,1,?,? FROM football_matches ORDER BY kickoff LIMIT 1
                """, UUID.randomUUID(), userId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("UPDATE football_matches SET status = 'FINISHED', home_score = 2, away_score = 1 WHERE id IN (SELECT match_id FROM predictions)");
        delivered = new CopyOnWriteArrayList<>();
        doAnswer(call -> { delivered.add(call.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), any());
    }

    @Test
    void knownAndUnknownEmailsHaveIdenticalResponsesAndRequestsLeaveCredentialsAndSessionsUsable() throws Exception {
        try (var owner = session(); var guest = session()) {
            assertThat(login(owner, OLD).statusCode()).isEqualTo(200);
            var before = accountSnapshot();
            var known = request(guest, "  RECOVERY@EXAMPLE.TEST ");
            var unknown = request(guest, "unknown@example.test");
            assertThat(known.statusCode()).isEqualTo(200);
            assertThat(unknown.statusCode()).isEqualTo(200);
            assertThat(known.body()).isEqualTo(unknown.body());
            assertThat(json.readTree(known.body()).get("message").asText()).isEqualTo(PasswordResetService.CONFIRMATION);
            assertThat(known.body()).doesNotContain(delivered.getFirst());
            assertThat(accountSnapshot()).isEqualTo(before);
            assertThat(owner.request("GET", "/users/me", null, null).statusCode()).isEqualTo(200);
            assertThat(login(guest, OLD).statusCode()).isEqualTo(200);
            verify(mail).send(eq(EMAIL), anyString(), eq(NOW.plusSeconds(1800)));
            verify(limiter).checkPasswordResetEmail(EMAIL);
            verify(limiter).checkPasswordResetEmail("unknown@example.test");
            assertThat(delivered.getFirst()).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(delivered.getFirst())).hasSize(32);
            assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class))
                    .matches("[a-f0-9]{64}").isNotEqualTo(delivered.getFirst());
        }
    }

    @Test
    void resetRevokesAllLinksAndSessionsPreservesAccountAndPredictionsAndRequiresNewPassword() throws Exception {
        try (var first = session(); var second = session(); var guest = session()) {
            assertThat(login(first, OLD).statusCode()).isEqualTo(200);
            assertThat(login(second, OLD).statusCode()).isEqualTo(200);
            var before = accountSnapshot();
            var predictions = jdbc.queryForList("SELECT * FROM predictions");
            var leaderboard = first.request("GET", "/leaderboard?competitionId=10000000-0000-0000-0000-000000000002&seasonId=10000000-0000-0000-0000-000000000003", null, null);
            assertThat(leaderboard.statusCode()).as(leaderboard.body()).isEqualTo(200);
            assertThat(json.readTree(leaderboard.body()).at("/items/0/totalPoints").asLong()).isEqualTo(10L);
            request(guest, EMAIL); request(guest, EMAIL);
            String older = delivered.getFirst(), newer = delivered.getLast();
            assertThat(older).isNotEqualTo(newer);
            assertThat(validate(guest, older).statusCode()).isEqualTo(204);
            assertThat(validate(guest, newer).statusCode()).isEqualTo(204);
            assertThat(confirm(first, older, NEW).statusCode()).isEqualTo(204);
            assertThat(first.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            assertThat(second.request("GET", "/predictions/me", null, null).statusCode()).isEqualTo(401);
            invalid(confirm(guest, older, "Another-password!"));
            invalid(confirm(guest, newer, "Another-password!"));
            invalid(validate(guest, newer));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens WHERE used_at IS NULL", Long.class)).isZero();
            assertThat(accountSnapshot()).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT * FROM predictions")).isEqualTo(predictions);
            assertThat(login(first, OLD).statusCode()).isEqualTo(401);
            assertThat(login(first, NEW).statusCode()).isEqualTo(200);
            assertThat(first.request("GET", "/leaderboard?competitionId=10000000-0000-0000-0000-000000000002&seasonId=10000000-0000-0000-0000-000000000003", null, null).body())
                    .isEqualTo(leaderboard.body());
            assertThat(jdbc.queryForObject("SELECT password_version FROM users", Long.class)).isEqualTo(1L);
        }
    }

    @Test
    void incorrectExpiredAndInvalidPasswordsDoNotConsumeValidLinksOrChangeAccounts() throws Exception {
        try (var guest = session()) {
            request(guest, EMAIL);
            String token = delivered.getFirst();
            var before = jdbc.queryForList("SELECT * FROM users");
            invalid(confirm(guest, "x".repeat(43), NEW));
            invalid(confirm(guest, "incorrect", NEW));
            assertThat(confirm(guest, token, "short").statusCode()).isEqualTo(400);
            assertThat(confirm(guest, token, "ă".repeat(37)).statusCode()).isEqualTo(400);
            assertThat(confirm(guest, token, " ".repeat(8)).statusCode()).isEqualTo(400);
            assertThat(validate(guest, token).statusCode()).isEqualTo(204);
            assertThat(jdbc.queryForList("SELECT * FROM users")).isEqualTo(before);
            when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
            invalid(validate(guest, token));
            invalid(confirm(guest, token, NEW));
            assertThat(jdbc.queryForList("SELECT * FROM users")).isEqualTo(before);
        }
    }

    @Test
    void deliveryFailureCanBeRetriedWithoutInvalidatingEarlierLinks() throws Exception {
        try (var guest = session()) {
            request(guest, EMAIL);
            String working = delivered.getFirst();
            var before = jdbc.queryForList("SELECT * FROM users");
            doThrow(new MailSendException("SMTP unavailable")).when(mail).send(anyString(), anyString(), any());
            var failed = request(guest, EMAIL);
            assertThat(failed.statusCode()).isEqualTo(200);
            assertThat(failed.body()).isEqualTo(request(guest, "unknown@example.test").body());
            assertThat(validate(guest, working).statusCode()).isEqualTo(204);
            assertThat(jdbc.queryForList("SELECT * FROM users")).isEqualTo(before);
            doAnswer(call -> { delivered.add(call.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), any());
            assertThat(request(guest, EMAIL).statusCode()).isEqualTo(200);
            assertThat(confirm(guest, delivered.getLast(), NEW).statusCode()).isEqualTo(204);
        }
    }

    @Test
    void passwordRecoveryDoesNotVerifyEmail() throws Exception {
        jdbc.update("UPDATE users SET email_verified = FALSE");
        try (var guest = session()) {
            request(guest, EMAIL);
            assertThat(confirm(guest, delivered.getFirst(), NEW).statusCode()).isEqualTo(204);
            assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isFalse();
            assertThat(login(guest, NEW).statusCode()).isEqualTo(202);
            assertThat(guest.request("GET", "/predictions/me", null, null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void concurrentSubmissionsOfSameAndDifferentLinksHaveExactlyOneWinner() throws Exception {
        for (boolean sameLink : List.of(true, false)) {
            try (var first = session(); var second = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
                request(first, EMAIL); request(second, EMAIL);
                String one = delivered.get(delivered.size() - 2), two = sameLink ? one : delivered.getLast();
                String csrfOne = first.csrf(), csrfTwo = second.csrf();
                var gate = new CyclicBarrier(2);
                var a = tasks.submit(() -> { gate.await(); return first.request("POST", "/auth/password-reset/confirm", Map.of("token", one, "newPassword", NEW), csrfOne); });
                var b = tasks.submit(() -> { gate.await(); return second.request("POST", "/auth/password-reset/confirm", Map.of("token", two, "newPassword", OLD), csrfTwo); });
                var responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
                assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(204, 400);
                invalid(responses.stream().filter(response -> response.statusCode() == 400).findFirst().orElseThrow());
            }
        }
        assertThat(jdbc.queryForObject("SELECT password_version FROM users", Long.class)).isEqualTo(2L);
    }

    @Test
    void rateLimitsCoverIpNormalizedEmailAndSubmissionsWithoutLeakingAccountData() throws Exception {
        try (var guest = session()) {
            doThrow(new AuthRateLimitException(3600)).when(limiter).checkPasswordResetEmail(EMAIL);
            var limited = request(guest, " RECOVERY@EXAMPLE.TEST ");
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).hasValue("3600");
            verifyNoInteractions(mail);
            reset(limiter);
            doThrow(new AuthRateLimitException(120)).when(limiter).checkPasswordResetIp(anyString());
            assertThat(request(guest, "unknown@example.test").statusCode()).isEqualTo(429);
            reset(limiter);
            doThrow(new AuthRateLimitException(120)).when(limiter).checkPasswordResetSubmissionIp(anyString());
            assertThat(confirm(guest, "x".repeat(43), NEW).statusCode()).isEqualTo(429);
            assertThat(validate(guest, "x".repeat(43)).statusCode()).isEqualTo(429);
            reset(limiter);
            doThrow(new AuthRateLimitException(120)).when(limiter).checkPasswordResetToken(anyString());
            assertThat(confirm(guest, "x".repeat(43), NEW).statusCode()).isEqualTo(429);
        }
    }

    @Test
    void publicOperationsStillRequireCsrf() throws Exception {
        try (var guest = session()) {
            assertThat(guest.request("POST", "/auth/password-reset/request", Map.of("email", EMAIL), null).statusCode()).isEqualTo(403);
            assertThat(guest.request("POST", "/auth/password-reset/confirm", Map.of("token", "x".repeat(43), "newPassword", NEW), null).statusCode()).isEqualTo(403);
            verifyNoInteractions(mail);
        }
    }

    private Map<String, Object> accountSnapshot() {
        return jdbc.queryForMap("SELECT id,display_name,display_name_key,email,email_verified,role,hide_predictions_until_kickoff,created_at FROM users WHERE id = ?", userId);
    }
    private void invalid(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("detail").asText()).isEqualTo(PasswordResetService.INVALID_LINK);
    }
    private TestHttpSession session() { return new TestHttpSession(port, json); }
    private HttpResponse<String> login(TestHttpSession session, String password) throws Exception {
        return session.request("POST", "/auth/login", Map.of("email", EMAIL, "password", password), session.csrf());
    }
    private HttpResponse<String> request(TestHttpSession session, String email) throws Exception {
        return session.request("POST", "/auth/password-reset/request", Map.of("email", email), session.csrf());
    }
    private HttpResponse<String> validate(TestHttpSession session, String token) throws Exception {
        return session.request("POST", "/auth/password-reset/validate", Map.of("token", token), session.csrf());
    }
    private HttpResponse<String> confirm(TestHttpSession session, String token, String password) throws Exception {
        return session.request("POST", "/auth/password-reset/confirm", Map.of("token", token, "newPassword", password), session.csrf());
    }
}

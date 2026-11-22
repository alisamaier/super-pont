package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.service.VerificationEmailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_VERIFICATION_DATABASE_URL:jdbc:h2:mem:verification;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
    "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false"
})
class RegistrationVerificationApiTests {
    private static final String PASSWORD = "verification password";
    private static final String ROOT = "/auth/registration";
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean Clock clock;
    @MockitoBean AuthRateLimiter limiter;
    @MockitoBean VerificationEmailSender mail;
    private Instant now;
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final List<String> recipients = new CopyOnWriteArrayList<>();
    private final AtomicBoolean deliveryFails = new AtomicBoolean();

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM users");
        now = Instant.parse("2026-11-19T12:00:00Z");
        when(clock.instant()).thenAnswer(call -> now);
        codes.clear();
        recipients.clear();
        deliveryFails.set(false);
        doAnswer(call -> {
            if (deliveryFails.get()) throw new MailSendException("Test SMTP unavailable");
            codes.put(call.getArgument(0), call.getArgument(1));
            recipients.add(call.getArgument(0));
            return null;
        }).when(mail).send(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void pendingCookieResumesButNeverAuthenticatesAndSuccessfulConfirmationIsIdempotent() throws Exception {
        try (var session = session(); var anonymous = session()) {
            var registered = register(session, "fan@example.test");
            assertThat(registered.statusCode()).isEqualTo(200);
            String cookie = registered.headers().allValues("Set-Cookie").stream()
                    .filter(value -> value.startsWith("SP_REGISTRATION=")).findFirst().orElseThrow();
            assertThat(cookie).contains("HttpOnly", "SameSite=lax", "Path=/api/auth/registration", "Max-Age=86400")
                    .doesNotContain("Secure");
            assertThat(registered.body()).doesNotContain("\"" + codes.get("fan@example.test") + "\"", PASSWORD, "tokenHash", "codeHash");
            var row = jdbc.queryForMap("SELECT * FROM registration_verifications");
            assertThat(row.get("TOKEN_HASH").toString()).hasSize(64);
            assertThat(passwords.matches(codes.get("fan@example.test"), row.get("CODE_HASH").toString())).isTrue();
            assertThat(get(session).get("email").asText()).isEqualTo("fan@example.test");
            for (String path : List.of("/users/me", "/predictions/me", "/leaderboard/superliga", "/users/me/settings"))
                assertThat(session.request("GET", path, null, null).statusCode()).isEqualTo(401);
            assertThat(session.request("PUT", "/users/me/settings", Map.of("hidePredictionsUntilKickoff", true), session.csrf())
                    .statusCode()).isEqualTo(401);
            assertThat(session.request("GET", "/matches", null, null).statusCode()).isEqualTo(200);
            assertThat(login(session, "fan@example.test", "wrong password").statusCode()).isEqualTo(401);
            assertThat(login(session, "fan@example.test", PASSWORD).statusCode()).isEqualTo(202);
            assertThat(session.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            assertThat(anonymous.request("GET", ROOT, null, null).statusCode()).isEqualTo(401);
            assertThat(session.request("POST", ROOT + "/confirm", Map.of("code", codes.get("fan@example.test")), null).statusCode()).isEqualTo(403);
            assertThat(anonymous.request("GET", ROOT, null, null, Map.of("Cookie", cookie.split(";", 2)[0])).statusCode()).isEqualTo(401);
            assertThat(confirm(session, codes.get("fan@example.test")).statusCode()).isEqualTo(200);
            assertThat(confirm(session, codes.get("fan@example.test")).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT code_hash FROM registration_verifications", String.class)).isNull();
            assertThat(session.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            assertThat(login(session, "fan@example.test", PASSWORD).statusCode()).isEqualTo(200);
            assertThat(session.request("GET", "/users/me", null, null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void fiveFailedAttemptsPersistAndResendingReplacesTheCodeAndResetsAttempts() throws Exception {
        try (var session = session()) {
            register(session, "attempts@example.test");
            String old = codes.get("attempts@example.test"), wrong = wrong(old);
            for (int i = 1; i <= 5; i++) {
                var failure = confirm(session, wrong);
                assertThat(failure.statusCode()).isEqualTo(400);
                assertThat(json.readTree(failure.body()).get("attemptsRemaining").asInt()).isEqualTo(5 - i);
            }
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM registration_verifications", Integer.class)).isEqualTo(5);
            assertThat(confirm(session, old).body()).contains("CODE_ATTEMPTS_EXHAUSTED");
            var limited = resend(session);
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(limited.headers().firstValue("Retry-After")).contains("60");
            now = now.plusSeconds(60);
            assertThat(resend(session).statusCode()).isEqualTo(200);
            String replacement = codes.get("attempts@example.test");
            assertThat(replacement).isNotEqualTo(old);
            assertThat(confirm(session, old).statusCode()).isEqualTo(400);
            assertThat(confirm(session, replacement).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void codesExpireAtTenMinutesAndTokensCanBeRenewedThroughCredentialCheckedLogin() throws Exception {
        try (var session = session()) {
            register(session, "expiry@example.test");
            String code = codes.get("expiry@example.test");
            now = now.plusSeconds(600);
            assertThat(confirm(session, code).statusCode()).isEqualTo(410);
            assertThat(confirm(session, code).body()).contains("CODE_EXPIRED");
            now = now.plusSeconds(86400);
            assertThat(session.request("GET", ROOT, null, null).statusCode()).isEqualTo(401);
            assertThat(login(session, "expiry@example.test", PASSWORD).statusCode()).isEqualTo(202);
            assertThat(get(session).get("state").asText()).isEqualTo("PENDING");
            assertThat(resend(session).statusCode()).isEqualTo(200);
            assertThat(confirm(session, codes.get("expiry@example.test")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void correctsOnlyPendingEmailsChecksUniquenessAndInvalidatesThePreviousCode() throws Exception {
        try (var session = session(); var other = session()) {
            register(session, "typo@example.test");
            register(other, "occupied@example.test");
            String old = codes.get("typo@example.test");
            now = now.plusSeconds(61);
            assertThat(changeEmail(session, "OCCUPIED@example.test").statusCode()).isEqualTo(409);
            assertThat(get(session).get("email").asText()).isEqualTo("typo@example.test");
            assertThat(changeEmail(session, "  Correct@EXAMPLE.test ").statusCode()).isEqualTo(200);
            assertThat(get(session).get("email").asText()).isEqualTo("correct@example.test");
            assertThat(confirm(session, old).statusCode()).isEqualTo(400);
            assertThat(confirm(session, codes.get("correct@example.test")).statusCode()).isEqualTo(200);
            assertThat(changeEmail(session, "another@example.test").statusCode()).isEqualTo(200);
            assertThat(get(session).get("email").asText()).isEqualTo("correct@example.test");
        }
    }

    @Test
    void deliveryFailureKeepsTheAccountAndCredentialLoginCanResumeAndRetry() throws Exception {
        try (var session = session()) {
            deliveryFails.set(true);
            var registered = register(session, "retry@example.test");
            assertThat(registered.statusCode()).isEqualTo(200);
            assertThat(json.readTree(registered.body()).get("deliveryStatus").asText()).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
            assertThat(register(session, "retry@example.test").statusCode()).isEqualTo(409);
            assertThat(login(session, "retry@example.test", PASSWORD).statusCode()).isEqualTo(202);
            deliveryFails.set(false);
            now = now.plusSeconds(61);
            assertThat(resend(session).statusCode()).isEqualTo(200);
            assertThat(confirm(session, codes.get("retry@example.test")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void hourlySendLimitsSurviveTokenRotationAndEmailCorrections() throws Exception {
        try (var session = session()) {
            register(session, "hourly@example.test");
            for (int i = 0; i < 5; i++) {
                now = now.plusSeconds(61);
                assertThat(resend(session).statusCode()).isEqualTo(200);
            }
            now = now.plusSeconds(61);
            assertThat(resend(session).statusCode()).isEqualTo(429);
            assertThat(login(session, "hourly@example.test", PASSWORD).statusCode()).isEqualTo(202);
            assertThat(resend(session).statusCode()).isEqualTo(429);
            assertThat(changeEmail(session, "bypass@example.test").statusCode()).isEqualTo(429);
            assertThat(recipients).hasSize(6);
            assertThat(get(session).get("email").asText()).isEqualTo("hourly@example.test");
            now = now.plusSeconds(3600);
            assertThat(resend(session).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void concurrentResendsAndConfirmationsUseOneConsistentCode() throws Exception {
        try (var session = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            register(session, "concurrent@example.test");
            now = now.plusSeconds(61);
            String csrf = session.csrf();
            var gate = new CyclicBarrier(2);
            var a = tasks.submit(() -> { gate.await(); return session.request("POST", ROOT + "/resend", null, csrf); });
            var b = tasks.submit(() -> { gate.await(); return session.request("POST", ROOT + "/resend", null, csrf); });
            assertThat(List.of(a.get(15, TimeUnit.SECONDS).statusCode(), b.get(15, TimeUnit.SECONDS).statusCode()))
                    .containsExactlyInAnyOrder(200, 429);
            assertThat(recipients).hasSize(2);
            var body = Map.of("code", codes.get("concurrent@example.test"));
            var gate2 = new CyclicBarrier(2);
            var c = tasks.submit(() -> { gate2.await(); return session.request("POST", ROOT + "/confirm", body, csrf); });
            var d = tasks.submit(() -> { gate2.await(); return session.request("POST", ROOT + "/confirm", body, csrf); });
            assertThat(List.of(c.get(15, TimeUnit.SECONDS).statusCode(), d.get(15, TimeUnit.SECONDS).statusCode()))
                    .containsExactly(200, 200);
        }
    }

    @Test
    void concurrentVerificationAndResendEitherConsumeOrReplaceTheOriginalCode() throws Exception {
        try (var session = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            register(session, "race@example.test");
            String old = codes.get("race@example.test");
            now = now.plusSeconds(61);
            String csrf = session.csrf();
            var gate = new CyclicBarrier(2);
            var verify = tasks.submit(() -> { gate.await(); return session.request("POST", ROOT + "/confirm", Map.of("code", old), csrf); });
            var resend = tasks.submit(() -> { gate.await(); return session.request("POST", ROOT + "/resend", null, csrf); });
            assertThat(resend.get(15, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(verify.get(15, TimeUnit.SECONDS).statusCode()).isIn(200, 400);
            assertThat(confirm(session, codes.get("race@example.test")).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isTrue();
        }
    }

    @Test
    void concurrentIncorrectCodesCannotExceedFiveAttempts() throws Exception {
        try (var session = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            register(session, "parallel-attempts@example.test");
            String csrf = session.csrf();
            var body = Map.of("code", wrong(codes.get("parallel-attempts@example.test")));
            var gate = new CyclicBarrier(8);
            var futures = new java.util.ArrayList<Future<Integer>>();
            for (int index = 0; index < 8; index++) futures.add(tasks.submit(() -> {
                gate.await();
                return session.request("POST", ROOT + "/confirm", body, csrf).statusCode();
            }));
            var statuses = new java.util.ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(400, 409);
            assertThat(statuses.stream().filter(status -> status == 400).count()).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM registration_verifications", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT email_verified FROM users", Boolean.class)).isFalse();
        }
    }

    private TestHttpSession session() { return new TestHttpSession(port, json); }
    private HttpResponse<String> register(TestHttpSession session, String email) throws Exception {
        return session.request("POST", "/auth/register", Map.of("displayName", "Fan " + email.split("@")[0], "email", email, "password", PASSWORD), session.csrf());
    }
    private HttpResponse<String> login(TestHttpSession session, String email, String password) throws Exception {
        return session.request("POST", "/auth/login", Map.of("email", email, "password", password), session.csrf());
    }
    private HttpResponse<String> confirm(TestHttpSession session, String code) throws Exception {
        return session.request("POST", ROOT + "/confirm", Map.of("code", code), session.csrf());
    }
    private HttpResponse<String> resend(TestHttpSession session) throws Exception {
        return session.request("POST", ROOT + "/resend", null, session.csrf());
    }
    private HttpResponse<String> changeEmail(TestHttpSession session, String email) throws Exception {
        return session.request("POST", ROOT + "/email", Map.of("email", email), session.csrf());
    }
    private JsonNode get(TestHttpSession session) throws Exception {
        var result = session.request("GET", ROOT, null, null);
        assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        return json.readTree(result.body());
    }
    private String wrong(String code) { return code.equals("000000") ? "000001" : "000000"; }
}

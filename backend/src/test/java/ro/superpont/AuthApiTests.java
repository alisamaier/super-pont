package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ro.superpont.auth.security.AuthRateLimiter;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false"
})
class AuthApiTests {
    private static final String PASSWORD = "correct horse 123";
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PasswordEncoder passwords;
    @MockitoBean AuthRateLimiter rateLimiter;

    @Test
    void registersLogsInRotatesSessionAndLogsOut() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            var csrfResponse = session.request("GET", "/auth/csrf", null, null);
            var csrf = json.readTree(csrfResponse.body()).get("token").asText();
            String anonymousCookie = sessionCookie(csrfResponse);
            assertThat(anonymousCookie).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
            assertThat(csrfResponse.headers().firstValue("Cache-Control")).hasValueSatisfying(
                    value -> assertThat(value).contains("no-store"));

            var registered = session.request("POST", "/auth/register",
                    Map.of("displayName", "MatchFan", "email", "  Fan@EXAMPLE.test  ",
                            "password", PASSWORD, "role", "ADMIN"), csrf);
            assertThat(registered.statusCode()).isEqualTo(200);
            var profile = json.readTree(registered.body());
            assertThat(profile.get("displayName").asText()).isEqualTo("MatchFan");
            assertThat(profile.get("email").asText()).isEqualTo("fan@example.test");
            assertThat(profile.get("role").asText()).isEqualTo("USER");
            assertThat(profile.has("passwordHash")).isFalse();
            assertThat(profile.has("username")).isFalse();
            assertThat(profile.hasNonNull("createdAt")).isTrue();
            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM users WHERE email = 'fan@example.test'", String.class);
            assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
            assertThat(passwords.matches(PASSWORD, hash)).isTrue();
            assertThat(session.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            assertThat(session.request("POST", "/auth/login",
                    Map.of("email", "fan@example.test", "password", "wrong password"), csrf).statusCode())
                    .isEqualTo(401);

            var loggedIn = session.request("POST", "/auth/login",
                    Map.of("email", "  FAN@EXAMPLE.TEST  ", "password", PASSWORD), csrf);
            assertThat(loggedIn.statusCode()).isEqualTo(200);
            String authenticatedCookie = sessionCookie(loggedIn);
            assertThat(authenticatedCookie).isNotEqualTo(anonymousCookie);
            assertThat(requestWithCookie(anonymousCookie).statusCode()).isEqualTo(401);
            var me = session.request("GET", "/users/me", null, null);
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(json.readTree(me.body()).get("id")).isEqualTo(profile.get("id"));
            assertThat(json.readTree(me.body()).has("passwordHash")).isFalse();

            assertThat(session.request("POST", "/auth/logout", null, csrf).statusCode()).isEqualTo(403);
            assertThat(session.request("POST", "/auth/logout", null, null).statusCode()).isEqualTo(403);
            assertThat(session.request("POST", "/auth/logout", null, session.csrf()).statusCode())
                    .isEqualTo(204);
            assertThat(requestWithCookie(authenticatedCookie).statusCode()).isEqualTo(401);
            assertThat(session.request("GET", "/users/me", null, null).statusCode()).isEqualTo(401);
            assertThat(session.request("GET", "/matches", null, null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void rejectsInvalidAndDuplicateAccountsAndRequiresCsrfOnAuthWrites() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            var credentials = registration("UniqueFan", "unique@example.test");
            assertThat(session.request("POST", "/auth/register", credentials, null).statusCode())
                    .isEqualTo(403);
            assertThat(session.request("POST", "/auth/login", credentials, null).statusCode())
                    .isEqualTo(403);
            String csrf = session.csrf();
            var invalid = session.request("POST", "/auth/register",
                    Map.of("displayName", "", "email", "invalid", "password", "short"), csrf);
            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(invalid.body()).contains("\"errors\"", "\"displayName\"", "\"email\"", "\"password\"");
            assertThat(session.request("POST", "/auth/register",
                    Map.of("displayName", "unicode", "email", "unicode@example.test",
                            "password", "é".repeat(37)), csrf).statusCode()).isEqualTo(400);
            assertThat(session.request("POST", "/auth/register", credentials, csrf).statusCode())
                    .isEqualTo(200);
            assertThat(session.request("POST", "/auth/register",
                    registration("UniqueFan", "another@example.test"), csrf).statusCode()).isEqualTo(200);
            assertConflicts(session.request("POST", "/auth/register",
                    registration("OtherFan", "  UNIQUE@example.test  "), csrf), "email");
            assertThat(session.request("POST", "/auth/register",
                    registration("SecondFan", "second@example.test"), csrf).statusCode()).isEqualTo(200);
            assertConflicts(session.request("POST", "/auth/register",
                    registration("UNIQUEFAN", "SECOND@example.test"), csrf), "email");
        }
    }

    @Test
    void onlyAnAdminWithCsrfCanWriteFootballData() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            var country = Map.of("name", "Test country", "isoCode", "XY");
            String csrf = session.csrf();
            assertThat(session.request("POST", "/countries", country, csrf).statusCode()).isEqualTo(401);
            assertThat(session.request("POST", "/auth/register",
                    registration("staff", "staff@example.test"), csrf).statusCode()).isEqualTo(200);
            var login = Map.of("email", "staff@example.test", "password", PASSWORD);
            assertThat(session.request("POST", "/auth/login", login, csrf).statusCode()).isEqualTo(200);
            csrf = session.csrf();
            assertThat(session.request("POST", "/countries", country, csrf).statusCode()).isEqualTo(403);
            assertThat(session.request("POST", "/auth/logout", null, csrf).statusCode()).isEqualTo(204);

            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'staff@example.test'");
            assertThat(session.request("POST", "/auth/login", login, session.csrf()).statusCode())
                    .isEqualTo(200);
            assertThat(session.request("POST", "/countries", country, null).statusCode()).isEqualTo(403);
            assertThat(session.request("POST", "/countries", country, session.csrf()).statusCode())
                    .isEqualTo(201);
        }
    }

    @Test
    void concurrentRegistrationUsesNormalizedEmailUniqueness() throws Exception {
        String email = "race-email@example.test";
        String password = "concurrent password";
        var bothValidated = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothValidated.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwords).encode(password);
        try (var first = new TestHttpSession(port, json);
                var second = new TestHttpSession(port, json);
                var requests = Executors.newVirtualThreadPerTaskExecutor()) {
            String firstCsrf = first.csrf(), secondCsrf = second.csrf();
            var firstResult = requests.submit(() -> first.request("POST", "/auth/register",
                    Map.of("displayName", "Același nume", "email", email, "password", password), firstCsrf));
            var secondResult = requests.submit(() -> second.request("POST", "/auth/register",
                    Map.of("displayName", "Același nume", "email", "  RACE-EMAIL@EXAMPLE.TEST  ", "password", password), secondCsrf));
            var a = firstResult.get(15, TimeUnit.SECONDS);
            var b = secondResult.get(15, TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(200, 409);
            assertConflicts(a.statusCode() == 409 ? a : b, "email");
        }
    }

    private void assertConflicts(HttpResponse<String> response, String... fields) {
        assertThat(response.statusCode()).isEqualTo(409);
        var errors = json.readTree(response.body()).path("errors");
        assertThat(errors.propertyNames()).containsExactlyInAnyOrder(fields);
        for (String field : fields) {
            assertThat(errors.path(field).asText()).isEqualTo("Email is already registered");
        }
    }

    private static Map<String, String> registration(String name, String email) {
        return Map.of("displayName", name, "email", email, "password", PASSWORD);
    }

    private static String sessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .findFirst().orElseThrow();
    }

    private HttpResponse<String> requestWithCookie(String cookie) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me"))
                    .header("Cookie", cookie.split(";", 2)[0]).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}

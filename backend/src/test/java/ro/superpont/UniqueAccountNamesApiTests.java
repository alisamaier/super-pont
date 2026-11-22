package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ro.superpont.auth.security.AuthRateLimiter;
import ro.superpont.auth.service.VerificationEmailSender;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_NAMES_DATABASE_URL:jdbc:h2:mem:unique_names;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
    "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false"
})
class UniqueAccountNamesApiTests {
    private static final String PASSWORD = "unique-name-password";
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PasswordEncoder passwords;
    @MockitoBean AuthRateLimiter limiter;
    @MockitoBean VerificationEmailSender mail;

    @BeforeEach
    void reset() { jdbc.update("DELETE FROM users"); }

    @Test
    void registrationRejectsCaseAndWhitespaceVariantsAndKeepsEmailBasedLogin() throws Exception {
        try (var owner = session(); var other = session()) {
            var first = register(owner, " \tAlisa\u00A0", "owner@example.test", PASSWORD);
            assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT display_name FROM users", String.class)).isEqualTo("Alisa");
            for (String name : List.of("Alisa", "alisa", " Alisa ", "\tALISA\n", "\u00A0aLiSa\u00A0"))
                nameConflict(register(other, name, "other@example.test", PASSWORD));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
            jdbc.update("UPDATE users SET email_verified = TRUE");
            assertThat(login(owner, "owner@example.test").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void profileRejectsTakenNamesAndAllowsOwnNameAndCapitalizationChanges() throws Exception {
        try (var owner = session(); var other = session()) {
            assertThat(register(owner, "Alisa", "owner@example.test", PASSWORD).statusCode()).isEqualTo(200);
            assertThat(register(other, "Radu", "other@example.test", PASSWORD).statusCode()).isEqualTo(200);
            jdbc.update("UPDATE users SET email_verified = TRUE");
            assertThat(login(owner, "owner@example.test").statusCode()).isEqualTo(200);
            assertThat(login(other, "other@example.test").statusCode()).isEqualTo(200);
            for (String name : List.of("Alisa", "alisa", " Alisa ", "\tALISA\u00A0")) {
                nameConflict(profile(other, name));
                var saved = profile(owner, name);
                assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
                assertThat(json.readTree(saved.body()).get("displayName").asText()).isEqualTo(name.replaceAll("^[\\s\\u00A0]+|[\\s\\u00A0]+$", ""));
            }
            assertThat(jdbc.queryForObject("SELECT display_name FROM users WHERE email = 'other@example.test'", String.class)).isEqualTo("Radu");
        }
    }

    @Test
    void romanianNamesAreCaseInsensitiveWithoutRemovingAccents() throws Exception {
        try (var owner = session(); var other = session()) {
            assertThat(register(owner, "Ștefan", "romanian@example.test", PASSWORD).statusCode()).isEqualTo(200);
            nameConflict(register(other, " șTEFAN ", "other@example.test", PASSWORD));
            assertThat(register(other, "Stefan", "unaccented@example.test", PASSWORD).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void concurrentRegistrationsCannotClaimTheSameName() throws Exception {
        String password = "concurrent-name-password";
        var gate = new CyclicBarrier(2);
        doAnswer(call -> { gate.await(10, TimeUnit.SECONDS); return call.callRealMethod(); }).when(passwords).encode(password);
        try (var first = session(); var second = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            String firstCsrf = first.csrf(), secondCsrf = second.csrf();
            var a = tasks.submit(() -> first.request("POST", "/auth/register", input("Alisa", "first@example.test", password), firstCsrf));
            var b = tasks.submit(() -> second.request("POST", "/auth/register", input(" alisa ", "second@example.test", password), secondCsrf));
            var one = a.get(20, TimeUnit.SECONDS); var two = b.get(20, TimeUnit.SECONDS);
            assertThat(List.of(one.statusCode(), two.statusCode())).containsExactlyInAnyOrder(200, 409);
            nameConflict(one.statusCode() == 409 ? one : two);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentProfileChangesCannotClaimTheSameName() throws Exception {
        try (var first = session(); var second = session(); var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            register(first, "First", "first@example.test", PASSWORD);
            register(second, "Second", "second@example.test", PASSWORD);
            jdbc.update("UPDATE users SET email_verified = TRUE");
            assertThat(login(first, "first@example.test").statusCode()).isEqualTo(200);
            assertThat(login(second, "second@example.test").statusCode()).isEqualTo(200);
            String aCsrf = first.csrf(), bCsrf = second.csrf();
            var gate = new CyclicBarrier(2);
            var a = tasks.submit(() -> { gate.await(); return first.request("PUT", "/users/me/profile", Map.of("displayName", "Shared name"), aCsrf); });
            var b = tasks.submit(() -> { gate.await(); return second.request("PUT", "/users/me/profile", Map.of("displayName", " SHARED NAME "), bCsrf); });
            var one = a.get(20, TimeUnit.SECONDS); var two = b.get(20, TimeUnit.SECONDS);
            assertThat(List.of(one.statusCode(), two.statusCode())).containsExactlyInAnyOrder(200, 409);
            nameConflict(one.statusCode() == 409 ? one : two);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE display_name_key = 'shared name'", Long.class)).isEqualTo(1);
        }
    }

    private void nameConflict(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(json.readTree(response.body()).at("/errors/displayName").asText()).isEqualTo("Display name is already registered");
    }
    private TestHttpSession session() { return new TestHttpSession(port, json); }
    private Map<String, String> input(String name, String email, String password) { return Map.of("displayName", name, "email", email, "password", password); }
    private HttpResponse<String> register(TestHttpSession session, String name, String email, String password) throws Exception {
        return session.request("POST", "/auth/register", input(name, email, password), session.csrf());
    }
    private HttpResponse<String> login(TestHttpSession session, String email) throws Exception {
        return session.request("POST", "/auth/login", Map.of("email", email, "password", PASSWORD), session.csrf());
    }
    private HttpResponse<String> profile(TestHttpSession session, String name) throws Exception {
        return session.request("PUT", "/users/me/profile", Map.of("displayName", name), session.csrf());
    }
}

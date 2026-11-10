package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static ro.superpont.AuthRateLimitApiTests.assertRateLimited;
import static ro.superpont.AuthRateLimitApiTests.login;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:dev-auth-rate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="
})
class AuthDevRateLimitApiTests {
    @LocalServerPort int port;
    @Autowired JsonMapper json;

    @Test
    void admitsFiveRegistrationAttemptsPerIpBeforeLimiting() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            String csrf = session.csrf();
            for (int i = 0; i < 5; i++) {
                assertThat(session.request("POST", "/auth/register", Map.of(), csrf).statusCode())
                        .isEqualTo(400);
            }
            assertRateLimited(session.request("POST", "/auth/register", Map.of(), csrf), 60);
        }
    }

    @Test
    void admitsTenLoginAttemptsPerIpAndRetainsTheUsernameLimit() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            String csrf = session.csrf();
            for (int i = 0; i < 5; i++) {
                assertThat(session.request("POST", "/auth/login", login("target"), csrf).statusCode())
                        .isEqualTo(401);
            }
            assertRateLimited(session.request("POST", "/auth/login", login("TARGET"), csrf), 60);

            for (int i = 0; i < 4; i++) {
                assertThat(session.request("POST", "/auth/login", login("other" + i), csrf).statusCode())
                        .isEqualTo(401);
            }
            assertRateLimited(session.request("POST", "/auth/login", login("another"), csrf), 60);
        }
    }
}

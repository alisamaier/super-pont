package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-rate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false",
        "server.tomcat.remoteip.internal-proxies=(?!)"
})
class AuthRateLimitApiTests {
    @LocalServerPort int port;
    @Autowired JsonMapper json;

    @Test
    void capsLoginByPeerIpEvenWithForgedForwardingHeaders() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            String csrf = session.csrf();
            for (int i = 1; i <= 5; i++) {
                var result = session.request("POST", "/auth/login", login("unknown" + i), csrf,
                        forwarded(i));
                assertThat(result.statusCode()).isEqualTo(401);
                assertThat(json.readTree(result.body()).path("detail").asText())
                        .isEqualTo("Invalid username or password");
            }
            var rejected = session.request("POST", "/auth/login", login("another"), csrf, forwarded(6));
            assertRateLimited(rejected, 60);
            assertThat(rejected.headers().firstValue("Access-Control-Expose-Headers"))
                    .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("Retry-After"));
            assertThat(session.request("POST", "/auth/login", login("another"), null, forwarded(7)).statusCode())
                    .isEqualTo(403);
        }
    }

    @Test
    void capsRegistrationBeforeRequestBodyValidation() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            String csrf = session.csrf();
            for (int i = 1; i <= 3; i++) {
                assertThat(session.request("POST", "/auth/register", Map.of(), csrf, forwarded(i)).statusCode())
                        .isEqualTo(400);
            }
            assertRateLimited(session.request("POST", "/auth/register", Map.of(), csrf, forwarded(4)), 600);
        }
    }

    private static Map<String, String> forwarded(int host) {
        String address = "192.0.2." + host;
        return Map.of("Origin", "http://localhost:5173", "X-Forwarded-For", address, "Forwarded", "for=" + address);
    }

    static Map<String, String> login(String username) {
        return Map.of("username", username, "password", "invalid password");
    }

    static void assertRateLimited(HttpResponse<String> response, int maxRetryAfter) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After"))
                .hasValueSatisfying(value -> assertThat(Long.parseLong(value)).isBetween(1L, (long) maxRetryAfter));
    }
}

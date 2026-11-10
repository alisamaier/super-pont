package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static ro.superpont.AuthRateLimitApiTests.assertRateLimited;
import static ro.superpont.AuthRateLimitApiTests.login;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-proxy-rate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.frontend-url=http://localhost:5173", "server.servlet.session.cookie.secure=false",
        "server.tomcat.remoteip.internal-proxies=127.0.0.1/32,::1/128"
})
class AuthTrustedProxyRateLimitTests {
    @LocalServerPort int port;
    @Autowired JsonMapper json;

    @Test
    void capsOneUsernameAcrossClientIpsRecognizedFromATrustedProxy() throws Exception {
        try (var session = new TestHttpSession(port, json)) {
            String csrf = session.csrf();
            for (int i = 1; i <= 5; i++) {
                String username = i % 2 == 0 ? "TARGET" : "target";
                assertThat(session.request("POST", "/auth/login", login(username), csrf,
                        Map.of("X-Forwarded-For", "192.0.2." + i)).statusCode()).isEqualTo(401);
            }
            var sixthIp = Map.of("X-Forwarded-For", "192.0.2.6");
            assertRateLimited(session.request("POST", "/auth/login", login("Target"), csrf, sixthIp), 60);
            assertThat(session.request("POST", "/auth/login", login("another"), csrf, sixthIp).statusCode())
                    .isEqualTo(401);
        }
    }
}

package ro.superpont;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:deployment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.frontend-url=https://frontend.example.test"
})
class DeploymentTests {
    @LocalServerPort
    private int port;

    private HttpResponse<String> request(String path, String origin, boolean preflight) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (origin != null) builder.header("Origin", origin);
        if (preflight) builder.header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody());
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void healthIsPublicWithoutDatabaseDetails() throws Exception {
        var response = request("/actuator/health", null, false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("jdbc:", "components");
    }

    @Test
    void configuredFrontendCanReadHealth() throws Exception {
        var response = request("/actuator/health", "https://frontend.example.test", false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://frontend.example.test");
    }

    @Test
    void configuredFrontendPassesPreflight() throws Exception {
        var response = request("/api/example", "https://frontend.example.test", true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("https://frontend.example.test");
    }

    @Test
    void permitsCredentialedAuthPreflightOnlyForTheConfiguredOrigin() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (String origin : new String[]{"https://frontend.example.test", "https://untrusted.example.test"}) {
                var request = HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/auth/login"))
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (origin.equals("https://frontend.example.test")) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
                    assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                            .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("X-CSRF-TOKEN"));
                } else {
                    assertThat(response.statusCode()).isEqualTo(403);
                    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
                    assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
                }
            }
        }
    }

    @Test
    void unconfiguredOriginIsRejected() throws Exception {
        var response = request("/actuator/health", "https://untrusted.example.test", true);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }
}

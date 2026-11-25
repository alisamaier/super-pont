package ro.superpont.auth.service;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import tools.jackson.databind.json.JsonMapper;

class BrevoEmailTransportTests {
    @Test void sendsBothAlternativesAndSuppressesProviderSecretsOnFailureWithoutRetry() throws Exception {
        var json = JsonMapper.builder().build();
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/email", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("api-key")).isEqualTo("test-key");
            var body = json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(body.path("sender").path("email").asText()).isEqualTo("sender@example.test");
            assertThat(body.path("to").get(0).path("email").asText()).isEqualTo("recipient@example.test");
            assertThat(body.path("textContent").asText()).isEqualTo("Cod: 001234");
            assertThat(body.path("htmlContent").asText()).isEqualTo("<p>Cod: 001234</p>");
            byte[] response = "provider-secret-and-token".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(count.incrementAndGet() == 1 ? 201 : 429, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            var transport = new BrevoEmailTransport("test-key", "Super Pont <sender@example.test>", json, client,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/email"));
            transport.send("recipient@example.test", "Confirmă", "Cod: 001234", "<p>Cod: 001234</p>");
            assertThatThrownBy(() -> transport.send("recipient@example.test", "Confirmă", "Cod: 001234", "<p>Cod: 001234</p>"))
                    .isInstanceOf(MailSendException.class).hasMessage("Account email delivery failed").hasNoCause();
            assertThat(count.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }
    @Test void requiresKeyBeforeAnyDeliveryAttempt() {
        assertThatThrownBy(() -> new BrevoEmailTransport("", "sender@example.test", JsonMapper.builder().build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BREVO_API_KEY");
    }
}

package ro.superpont.auth.service;

import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.mail.MailSendException;
import tools.jackson.databind.json.JsonMapper;

/** HTTPS 443 works on Render Free. Never propagate provider bodies, keys or token-bearing messages. */
public class BrevoEmailTransport implements AccountEmailTransport {
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    private final String apiKey;
    private final Map<String, String> sender;
    private final JsonMapper json;
    private final HttpClient client;
    private final URI endpoint;

    public BrevoEmailTransport(String apiKey, String from, JsonMapper json) {
        this(apiKey, from, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), ENDPOINT);
    }

    // Package-private seam for offline HTTP integration tests; production endpoint is fixed.
    BrevoEmailTransport(String apiKey, String from, JsonMapper json, HttpClient client, URI endpoint) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("BREVO_API_KEY is required for brevo");
        try {
            var address = new InternetAddress(from, true);
            address.validate();
            sender = Map.of("email", address.getAddress(), "name",
                    address.getPersonal() == null ? "Super Pont" : address.getPersonal());
        } catch (Exception e) { throw new IllegalArgumentException("MAIL_FROM must be a valid sender address"); }
        this.apiKey = apiKey; this.json = json; this.client = client; this.endpoint = endpoint;
    }

    public void send(String recipient, String subject, String text, String html) {
        try {
            String body = json.writeValueAsString(Map.of("sender", sender,
                    "to", List.of(Map.of("email", recipient)), "subject", subject,
                    "textContent", text, "htmlContent", html));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .header("api-key", apiKey).header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 201) throw new MailSendException("Account email delivery failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Account email delivery interrupted");
        } catch (Exception e) {
            // Do not attach the cause: HTTP exceptions may contain credentials or response details.
            throw new MailSendException("Account email delivery failed");
        }
    }
}

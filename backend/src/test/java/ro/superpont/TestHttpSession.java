package ro.superpont;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

public final class TestHttpSession implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    private final String origin;
    private final JsonMapper json;

    public TestHttpSession(int port, JsonMapper json) {
        this.origin = "http://localhost:" + port;
        this.json = json;
    }

    public HttpResponse<String> request(String method, String path, Object body, String csrf)
            throws Exception {
        return request(method, path, body, csrf, Map.of());
    }

    public HttpResponse<String> request(String method, String path, Object body, String csrf,
            Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + "/api" + path));
        headers.forEach(request::header);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public String csrf() throws Exception {
        return json.readTree(request("GET", "/auth/csrf", null, null).body()).get("token").asText();
    }

    @Override
    public void close() {
        client.close();
    }
}

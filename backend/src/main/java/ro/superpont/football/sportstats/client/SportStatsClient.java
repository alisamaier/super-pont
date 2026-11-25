package ro.superpont.football.sportstats.client;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SportStatsClient {
    private static final URI ENDPOINT = URI.create("https://api.sportstatsapi.com/api/v1/soccer/matches");
    private final SportStatsProperties properties;
    private final JsonMapper json;
    private final HttpClient http;
    private final URI endpoint;
    private final URI imageOrigin;

    @Autowired
    public SportStatsClient(SportStatsProperties properties, JsonMapper json) { this(properties, json, ENDPOINT); }

    // Alternate endpoint is injectable in offline HTTP tests only; no environment or API input can change it.
    public SportStatsClient(SportStatsProperties properties, JsonMapper json, URI endpoint) {
        this(properties, json, endpoint, URI.create("https://img.sportstatsapi.com"));
    }

    // Image origin can only be overridden by offline tests, never through request/configuration input.
    public SportStatsClient(SportStatsProperties properties, JsonMapper json, URI endpoint, URI imageOrigin) {
        this.properties = properties;
        this.json = json;
        this.endpoint = endpoint;
        this.imageOrigin = imageOrigin;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public record Batch(List<JsonNode> matches, int pages) {}

    public Batch fetch(LocalDate from, LocalDate to) {
        var uri = UriComponentsBuilder.fromUri(endpoint).queryParam("league_name", "Romania: Superliga")
                .queryParam("date_from", from).queryParam("date_to", to).build().encode().toUri();
        return fetchRows(uri);
    }

    public List<JsonNode> fetchTeams(String leagueId) {
        var uri = UriComponentsBuilder.fromUri(endpoint.resolve("teams")).queryParam("league_id", leagueId).build().encode().toUri();
        return fetchRows(uri).matches();
    }

    private Batch fetchRows(URI base) {
        String key = properties.apiKey();
        if (key == null || key.isBlank()) throw new SportStatsException("API_KEY_MISSING");
        var matches = new ArrayList<JsonNode>();
        var seen = new HashSet<String>();
        int offset = 0;
        for (int page = 1; page <= properties.maxPages(); page++) {
            var uri = UriComponentsBuilder.fromUri(base)
                    .queryParam("limit", 100).queryParam("offset", offset).build(true).toUri();
            var response = get(uri, true, "application/json", 2 * 1024 * 1024);
            Instant retry = checkStatus(response.statusCode(), response.headers());
            JsonNode root;
            try { root = json.readTree(response.body()); }
            catch (Exception exception) { throw new SportStatsException("INVALID_API_JSON"); }
            if (root == null || !root.path("success").asBoolean(false)) {
                String code = root == null ? "" : root.path("error").path("code").asText("");
                throw new SportStatsException("PAYMENT_REQUIRED".equals(code) ? "PAYMENT_REQUIRED" : "API_REJECTED", retry);
            }
            var data = root.path("data");
            var meta = root.path("meta");
            if (!data.isArray() || !meta.path("total").isIntegralNumber() || !meta.path("total").canConvertToInt()
                    || !meta.path("offset").isIntegralNumber() || !meta.path("offset").canConvertToInt()
                    || !meta.path("limit").isIntegralNumber() || !meta.path("limit").canConvertToInt()) throw new SportStatsException("INVALID_API_PAGINATION");
            int total = meta.path("total").asInt(), limit = meta.path("limit").asInt();
            if (total == 0 && data.isEmpty() && offset == 0 && meta.path("offset").asInt() == 0 && limit >= 0 && limit <= 100)
                return new Batch(List.of(), page);
            if (total < 0 || limit < 1 || limit > 100 || meta.path("offset").asInt() != offset || data.size() > limit
                    || (long) offset + data.size() > total) throw new SportStatsException("INVALID_API_PAGINATION");
            for (var match : data) {
                String code = match.path("code").asText(match.path("id").asText(""));
                if (!code.isBlank() && !seen.add(code)) throw new SportStatsException("UNSTABLE_API_PAGINATION");
                matches.add(match);
            }
            if ((long) offset + data.size() >= total) return new Batch(List.copyOf(matches), page);
            if (data.size() != limit) throw new SportStatsException("INCOMPLETE_API_PAGE");
            offset = Math.addExact(offset, limit);
        }
        throw new SportStatsException("API_PAGE_LIMIT");
    }

    private HttpResponse<byte[]> get(URI uri, boolean authenticated, String accept, int maxBytes) {
            CompletableFuture<HttpResponse<byte[]>> pending = null;
            try {
                var request = HttpRequest.newBuilder(uri).timeout(properties.requestTimeout())
                        .header("Accept", accept);
                if (authenticated) {
                    if (properties.apiKey() == null || properties.apiKey().isBlank()) throw new SportStatsException("API_KEY_MISSING");
                    request.header("Authorization", "Bearer " + properties.apiKey().strip());
                }
                pending = http.sendAsync(request.GET().build(), info -> {
                    // Error status and Retry-After are handled without buffering an upstream error page.
                    if (info.statusCode() != 200) return HttpResponse.BodySubscribers.replacing(new byte[0]);
                    return new LimitedBody(maxBytes);
                });
                return pending.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (SportStatsException exception) { throw exception;
            } catch (InterruptedException exception) {
                if (pending != null) pending.cancel(true);
                Thread.currentThread().interrupt();
                throw new SportStatsException("API_INTERRUPTED");
            } catch (TimeoutException exception) {
                if (pending != null) pending.cancel(true);
                throw new SportStatsException("API_TIMEOUT");
            } catch (ExecutionException exception) {
                if (pending != null) pending.cancel(true);
                throw new SportStatsException(exception.getCause() instanceof HttpTimeoutException ? "API_TIMEOUT" : "API_UNAVAILABLE");
            } catch (Exception exception) {
                if (pending != null) pending.cancel(true);
                throw new SportStatsException("API_UNAVAILABLE");
            }
    }

    private Instant checkStatus(int status, HttpHeaders headers) {
            Instant retry = retryAfter(headers.firstValue("Retry-After").orElse(null));
            if (status == 401 || status == 403) throw new SportStatsException("API_AUTHENTICATION", retry);
            if (status == 402) throw new SportStatsException("PAYMENT_REQUIRED", retry);
            if (status == 429) throw new SportStatsException("API_RATE_LIMITED", retry == null ? Instant.now().plusSeconds(60) : retry);
            if (status == 404) throw new SportStatsException("LOGO_NOT_FOUND", retry);
            if (status != 200) throw new SportStatsException("API_HTTP_ERROR", retry);
            return retry;
    }

    public record LogoSource(URI uri, Long numericId, String credit) {}
    public record Image(byte[] bytes, String contentType) {}

    public LogoSource logoSource(JsonNode row) {
        String code = row.path("code").asText("");
        Long id = row.path("id").isIntegralNumber() && row.path("id").canConvertToLong() && row.path("id").asLong() > 0
                ? row.path("id").asLong() : null;
        String credit = row.path("logo_credit").asText(null);
        if (credit != null && (credit.length() > 1000 || (properties.apiKey() != null && !properties.apiKey().isBlank() && credit.contains(properties.apiKey()))))
            throw new SportStatsException("INVALID_LOGO_METADATA");
        for (String field : List.of("logo_thumb_url", "logo_url")) {
            String value = row.path(field).asText("");
            if (!value.isBlank()) {
                URI uri;
                try { uri = URI.create(value); } catch (Exception e) { throw new SportStatsException("UNTRUSTED_LOGO_SOURCE"); }
                if (!trustedImage(uri) || !uri.getPath().matches("/logos/soccer/" + java.util.regex.Pattern.quote(code.isBlank() ? String.valueOf(id) : code) + "[-.][a-zA-Z0-9.-]+"))
                    throw new SportStatsException("UNTRUSTED_LOGO_SOURCE");
                return new LogoSource(uri, id, credit);
            }
        }
        // The documented numeric route is only used with an ID explicitly supplied by the catalog.
        return id == null ? null : new LogoSource(endpoint.resolve("teams/" + id + "/logo"), id, credit);
    }

    private boolean trustedImage(URI uri) {
        return uri.getScheme() != null && uri.getScheme().equals(imageOrigin.getScheme())
                && imageOrigin.getHost().equals(uri.getHost()) && uri.getPort() == imageOrigin.getPort()
                && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getFragment() == null
                && uri.toASCIIString().length() <= 2048;
    }

    public Image downloadLogo(LogoSource source) {
        boolean authenticated = source.numericId() != null && source.uri().equals(endpoint.resolve("teams/" + source.numericId() + "/logo"));
        if (!authenticated && (!trustedImage(source.uri()) || !source.uri().getPath().matches("/logos/soccer/[a-zA-Z0-9.-]+")))
            throw new SportStatsException("UNTRUSTED_LOGO_SOURCE");
        var response = get(source.uri(), authenticated, "image/png,image/webp,image/jpeg", 2 * 1024 * 1024);
        checkStatus(response.statusCode(), response.headers());
        return new Image(response.body(), response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip().toLowerCase(Locale.ROOT));
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private long received;
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) received += buffer.remaining();
            if (received > limit) { subscription.cancel(); delegate.onError(new IOException("BODY_SIZE_LIMIT")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }

    static Instant retryAfter(String value) {
        if (value == null) return null;
        try { return Instant.now().plusSeconds(Math.max(0, Long.parseLong(value))); }
        catch (Exception ignored) {
            try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
            catch (Exception invalid) { return null; }
        }
    }

    @PreDestroy public void close() { http.shutdownNow(); }
}

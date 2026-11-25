package ro.superpont;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ro.superpont.football.team.model.TeamLogoProperties;
import ro.superpont.football.team.service.TeamLogoCache;
import ro.superpont.football.sportstats.client.*;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import tools.jackson.databind.json.JsonMapper;

class TeamLogoTests {
    @TempDir Path root;
    HttpServer server;
    SportStatsClient client;
    JsonMapper json = JsonMapper.builder().build();
    AtomicInteger requests;
    List<String> auth;
    byte[] body;
    int status;
    String type, retry;
    boolean paged;
    List<String> queries;

    static byte[] png(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }
    @BeforeEach void setup() throws Exception {
        requests = new AtomicInteger(); auth = new ArrayList<>(); queries = new ArrayList<>(); status = 200; type = "image/png"; body = png(16, 20);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            queries.add(exchange.getRequestURI().getQuery());
            byte[] content = body;
            if (paged) {
                int offset = exchange.getRequestURI().getQuery().contains("offset=1") ? 1 : 0;
                content = ("{\"success\":true,\"data\":[{\"code\":\"t" + offset + "\"}],\"meta\":{\"offset\":" + offset + ",\"limit\":1,\"total\":2}}").getBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", type);
            if (retry != null) exchange.getResponseHeaders().set("Retry-After", retry);
            exchange.getResponseHeaders().set("Location", "/forbidden-redirect");
            exchange.sendResponseHeaders(status, content.length);
            try { exchange.getResponseBody().write(content); } catch (IOException ignored) {} finally { exchange.close(); }
        });
        server.start();
        var properties = new SportStatsProperties("offline-secret", false, Duration.ofMinutes(30), Duration.ofSeconds(1), Duration.ofSeconds(3), 5, 7, 14, 7, 1, "dx2=REGULAR");
        var origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new SportStatsClient(properties, json, origin.resolve("/matches"), origin);
    }
    @AfterEach void stop() { client.close(); server.stop(0); }
    private SportStatsClient.LogoSource source() { return client.logoSource(json.readTree("{\"code\":\"t1nc\",\"logo_thumb_url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/logos/soccer/t1nc-123-128.webp\"}")); }

    @Test void decodesRasterAndKeepsTransparencyInPersistentContentAddressedCache() throws Exception {
        var cache = new TeamLogoCache(new ro.superpont.football.team.service.FileTeamLogoStore(new TeamLogoProperties(root)));
        var image = client.downloadLogo(source());
        String hash = cache.store(image.bytes(), image.contentType());
        assertThat(cache.contains(hash)).isTrue();
        var recreated = new TeamLogoCache(new ro.superpont.football.team.service.FileTeamLogoStore(new TeamLogoProperties(root)));
        assertThat(recreated.contains(hash)).isTrue();
        assertThat(recreated.store(body, type)).isEqualTo(hash);
        try (var files = Files.list(root)) { assertThat(files.count()).isEqualTo(1); }
        assertThat(ImageIO.read(new ByteArrayInputStream(cache.read(hash))).getColorModel().hasAlpha()).isTrue();
        assertThat(auth).containsExactly((String) null); // Bearer credentials never reach the CDN.
    }
    @Test void rejectsFakeMimeUnsupportedSvgOversizedAndHugeImages() throws Exception {
        var cache = new TeamLogoCache(new ro.superpont.football.team.service.FileTeamLogoStore(new TeamLogoProperties(root)));
        assertThatThrownBy(() -> cache.store(body, "image/jpeg")).isInstanceOf(SportStatsException.class);
        assertThatThrownBy(() -> cache.store("<svg/>".getBytes(), "image/svg+xml")).isInstanceOf(SportStatsException.class);
        assertThatThrownBy(() -> cache.store(new byte[TeamLogoCache.MAX_BYTES + 1], "image/png")).isInstanceOf(SportStatsException.class);
        var huge = png(2049, 1);
        assertThatThrownBy(() -> cache.store(huge, "image/png")).hasMessage("INVALID_LOGO_DIMENSIONS");
        assertThatThrownBy(() -> cache.store("not an image".getBytes(), "image/png")).isInstanceOf(SportStatsException.class);
        assertThatThrownBy(() -> cache.read("../../secret")).isInstanceOf(SportStatsException.class);
    }
    @Test void decodesProviderWebpThumbnailsToPng() {
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        var cache = new TeamLogoCache(new ro.superpont.football.team.service.FileTeamLogoStore(new TeamLogoProperties(root)));
        assertThat(cache.contains(cache.store(webp, "image/webp"))).isTrue();
    }
    @Test void neverTreatsMatchCodeAsNumericLogoIdAndPrefersExplicitThumbnail() {
        assertThat(client.logoSource(json.readTree("{\"code\":\"t1nc\"}"))).isNull();
        var source = client.logoSource(json.readTree("{\"code\":\"t1nc\",\"id\":123,\"logo_credit\":\"Credit\"}"));
        assertThat(source.uri().getPath()).isEqualTo("/teams/123/logo");
        client.downloadLogo(source);
        assertThat(auth).containsExactly("Bearer offline-secret");
        assertThat(source.credit()).isEqualTo("Credit");
        assertThat(source().numericId()).isNull();
    }
    @Test void rejectsArbitraryHostsQueriesRedirectsWrongTeamAndCredentials() {
        for (String url : List.of("http://localhost/secret", "http://169.254.169.254/latest/meta-data", "https://img.sportstatsapi.com.evil.test/logos/soccer/t1nc-x.png",
                "https://img.sportstatsapi.com/logos/soccer/t1nc-x.png?key=secret", "https://user:pass@img.sportstatsapi.com/logos/soccer/t1nc-x.png"))
            assertThatThrownBy(() -> client.logoSource(json.readTree(json.writeValueAsString(Map.of("code", "t1nc", "logo_url", url))))).hasMessage("UNTRUSTED_LOGO_SOURCE");
        var own = source().uri().toString().replace("t1nc-", "t1nd-");
        assertThatThrownBy(() -> client.logoSource(json.readTree(json.writeValueAsString(Map.of("code", "t1nc", "logo_url", own))))).hasMessage("UNTRUSTED_LOGO_SOURCE");
        status = 302;
        assertThatThrownBy(() -> client.downloadLogo(source())).hasMessage("API_HTTP_ERROR");
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void honorsNotFoundRateLimitAndPaymentWithoutRetryOrAutomaticPayment() {
        status = 404;
        assertThatThrownBy(() -> client.downloadLogo(source())).hasMessage("LOGO_NOT_FOUND");
        status = 429; retry = "120";
        var start = Instant.now();
        assertThatThrownBy(() -> client.downloadLogo(source())).isInstanceOfSatisfying(SportStatsException.class,
                e -> { assertThat(e.code()).isEqualTo("API_RATE_LIMITED"); assertThat(e.retryAfter()).isAfter(start.plusSeconds(119)); });
        status = 402;
        assertThatThrownBy(() -> client.downloadLogo(source())).hasMessage("PAYMENT_REQUIRED");
        assertThat(requests.get()).isEqualTo(3);
    }
    @Test void readsTeamPaginationAndSendsBearerOnlyToTheApi() {
        type = "application/json";
        body = "{\"success\":true,\"data\":[{\"code\":\"t1nc\",\"name\":\"FCSB\"}],\"meta\":{\"offset\":0,\"limit\":100,\"total\":1}}".getBytes();
        assertThat(client.fetchTeams("l60")).hasSize(1);
        assertThat(auth).containsExactly("Bearer offline-secret");
        queries.clear(); paged = true;
        assertThat(client.fetchTeams("l60")).extracting(row -> row.path("code").asText("")).containsExactly("t0", "t1");
        assertThat(queries).containsExactly("league_id=l60&limit=100&offset=0", "league_id=l60&limit=100&offset=1");
    }
}

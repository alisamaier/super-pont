package ro.superpont;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ro.superpont.football.team.repository.TeamLogoImageRepository;
import ro.superpont.football.team.service.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:hosted;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.team-logos.storage=database"
})
@ActiveProfiles("test")
class HostedStorageTests {
    @Autowired TeamLogoCache cache;
    @Autowired TeamLogoStore store;
    @Autowired TeamLogoImageRepository images;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @Test void migratesAnEmptyHostedDatabaseAndReadsLogosFromANewCacheWithoutDisk() throws Exception {
        assertThat(store).isInstanceOf(DatabaseTeamLogoStore.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM football_matches", Long.class)).isZero();
        String hash = cache.store(TeamLogoTests.png(16, 20), "image/png");
        var recreated = new TeamLogoCache(new DatabaseTeamLogoStore(images));
        assertThat(recreated.contains(hash)).isTrue();
        assertThat(recreated.read(hash)).isEqualTo(cache.read(hash));
        assertThat(recreated.store(cache.read(hash), "image/png")).isEqualTo(hash);
        assertThat(images.count()).isEqualTo(1);
    }

    @Test void exposesOnlyMinimalHealthAndKeepsCsrfSessionResponsesUncached() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
            var csrf = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(csrf.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(csrf.headers().firstValue("Set-Cookie").orElse("")).contains("HttpOnly");
        }
    }
}

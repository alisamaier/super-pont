package ro.superpont.football;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = "spring.datasource.url=jdbc:h2:mem:devseed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"dev", "test"})
class DevelopmentSeedTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void developmentSeedIsIsolatedAndIdempotent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competitions", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM competitions", String.class))
                .isEqualTo("SuperLiga");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teams", Long.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competition_phases", Long.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM football_matches", Long.class))
                .isEqualTo(4);
        new ResourceDatabasePopulator(new ClassPathResource("db/devdata/R__demo_superliga.sql"))
                .execute(flyway.getConfiguration().getDataSource());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM teams", Long.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM football_matches", Long.class))
                .isEqualTo(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}

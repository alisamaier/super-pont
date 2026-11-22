package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class EmailAuthenticationMigrationTests {
    @Test
    void normalizesLegacyAccountsWithoutChangingIdentityPasswordOrPredictions() throws Exception {
        try (var connection = DriverManager.getConnection(url(), "sa", "")) {
            var source = new SingleConnectionDataSource(connection, true);
            legacy(source);
            new ResourceDatabasePopulator(new ClassPathResource("db/devdata/R__demo_superliga.sql")).execute(source);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (id, username, email, password_hash, role, created_at, hide_predictions_until_kickoff)
                        VALUES ('30000000-0000-0000-0000-000000000001', 'legacy_public_name', '  legacy@example.test  ',
                                'unchanged-password-hash', 'USER', CURRENT_TIMESTAMP, TRUE)
                        """);
                statement.executeUpdate("""
                        INSERT INTO predictions (id, user_id, match_id, home_score, away_score, created_at, updated_at)
                        VALUES ('30000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001',
                                '10000000-0000-0000-0000-000000000040', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);
                Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
                try (var rows = statement.executeQuery("SELECT id, email, display_name, password_hash, password_version, hide_predictions_until_kickoff, email_verified FROM users")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("id")).isEqualTo("30000000-0000-0000-0000-000000000001");
                    assertThat(rows.getString("email")).isEqualTo("legacy@example.test");
                    assertThat(rows.getString("display_name")).isEqualTo("legacy_public_name");
                    assertThat(rows.getString("password_hash")).isEqualTo("unchanged-password-hash");
                    assertThat(rows.getLong("password_version")).isEqualTo(1);
                    assertThat(rows.getBoolean("hide_predictions_until_kickoff")).isTrue();
                    assertThat(rows.getBoolean("email_verified")).isTrue();
                }
                try (var rows = statement.executeQuery("SELECT home_score, away_score FROM predictions WHERE user_id = '30000000-0000-0000-0000-000000000001'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                    assertThat(rows.getInt(2)).isEqualTo(1);
                }
                assertThatThrownBy(() -> statement.executeQuery("SELECT username FROM users")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO users (id, display_name, email, password_hash, role, created_at)
                        VALUES (RANDOM_UUID(), 'Another name', ' LEGACY@example.test ', 'hash', 'USER', CURRENT_TIMESTAMP)
                        """)).isInstanceOf(SQLException.class);
                statement.executeUpdate("""
                        INSERT INTO users (id, display_name, email, password_hash, role, created_at)
                        VALUES (RANDOM_UUID(), 'Another public name', 'different@example.test', 'hash', 'USER', CURRENT_TIMESTAMP)
                        """);
                try (var rows = statement.executeQuery("SELECT email_verified FROM users WHERE email = 'different@example.test'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isFalse();
                }
            }
        }
    }

    @Test
    void reportsDuplicateEmailAccountIdsBeforeChangingAnyRows() throws Exception {
        try (var connection = DriverManager.getConnection(url(), "sa", "")) {
            var source = new SingleConnectionDataSource(connection, true);
            legacy(source);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users (id, username, email, password_hash, role, created_at)
                        VALUES ('30000000-0000-0000-0000-000000000001', 'first', 'same@example.test', 'hash', 'USER', CURRENT_TIMESTAMP),
                               ('30000000-0000-0000-0000-000000000002', 'second', ' same@example.test ', 'hash', 'USER', CURRENT_TIMESTAMP)
                        """);
                assertThatThrownBy(() -> Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate())
                        .isInstanceOf(FlywayException.class).hasStackTraceContaining("Duplicate normalized email")
                        .hasStackTraceContaining("30000000-0000-0000-0000-000000000001")
                        .hasStackTraceContaining("30000000-0000-0000-0000-000000000002");
                try (var rows = statement.executeQuery("SELECT email, password_version FROM users WHERE username = 'second'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(" same@example.test ");
                    assertThat(rows.getLong(2)).isZero();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email"})
    void reportsInvalidOrMissingEmailsBeforeSchemaChanges(String email) throws Exception {
        try (var connection = DriverManager.getConnection(url(), "sa", "")) {
            var source = new SingleConnectionDataSource(connection, true);
            legacy(source);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at)
                    VALUES ('30000000-0000-0000-0000-000000000003', 'legacy', ?, 'hash', 'USER', CURRENT_TIMESTAMP)
                    """)) {
                insert.setString(1, email);
                insert.executeUpdate();
            }
            assertThatThrownBy(() -> Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate())
                    .isInstanceOf(FlywayException.class).hasStackTraceContaining("Missing or invalid email")
                    .hasStackTraceContaining("30000000-0000-0000-0000-000000000003");
        }
    }

    private String url() { return "jdbc:h2:mem:email_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"; }
    private void legacy(SingleConnectionDataSource source) {
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("6").load().migrate();
    }
}

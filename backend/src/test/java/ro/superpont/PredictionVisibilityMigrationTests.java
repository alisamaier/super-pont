package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class PredictionVisibilityMigrationTests {
    @Test
    void changesTheDefaultForNewAccountsAndPreservesBothExistingPreferences() throws Exception {
        String url = "jdbc:h2:mem:visibility_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            var source = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("4").load().migrate();
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at)
                    VALUES ('30000000-0000-0000-0000-000000000001', 'existing_user', 'existing@example.test',
                            'unused-test-hash', 'USER', CURRENT_TIMESTAMP)
                    """);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("5").load().migrate();
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at, hide_predictions_until_kickoff)
                    VALUES ('30000000-0000-0000-0000-000000000002', 'existing_public', 'public@example.test',
                            'unused-test-hash', 'USER', CURRENT_TIMESTAMP, FALSE)
                    """);
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target("6").load().migrate();
            try (var rows = statement.executeQuery("SELECT hide_predictions_until_kickoff FROM users WHERE username = 'existing_user'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
                assertThat(rows.wasNull()).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT hide_predictions_until_kickoff FROM users WHERE username = 'existing_public'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, password_hash, role, created_at)
                    VALUES ('30000000-0000-0000-0000-000000000003', 'new_user', 'new@example.test',
                            'unused-test-hash', 'USER', CURRENT_TIMESTAMP)
                    """);
            try (var rows = statement.executeQuery("SELECT hide_predictions_until_kickoff, password_version FROM users WHERE username = 'new_user'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
                assertThat(rows.getLong(2)).isZero();
            }
        }
    }
}

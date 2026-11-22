package ro.superpont;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class UniqueAccountNamesMigrationTests {
    @Test
    void renamesDuplicatesWithoutChangingIdentityOrAssociatedData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:name_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            verifyMigration(connection);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_NAME_MIGRATION_DATABASE_URL", matches = ".+")
    void postgresRenamesAndEnforcesTheSameRule() throws Exception {
        String url = System.getenv("TEST_NAME_MIGRATION_DATABASE_URL");
        // This optional destructive fixture test accepts only its dedicated disposable local database.
        assertThat(url).matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5433/superpont_names_migration_test");
        try (var connection = DriverManager.getConnection(url, System.getenv("TEST_DATABASE_USERNAME"), System.getenv("TEST_DATABASE_PASSWORD"))) {
            verifyMigration(connection);
        }
    }

    private void verifyMigration(Connection connection) throws Exception {
        var source = new SingleConnectionDataSource(connection, true);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("9").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource("db/devdata/R__demo_superliga.sql")).execute(source);
        String longName = "x".repeat(64);
        String[] names = {" Alisa ", "alisa", "ALISA", "Alisa 2", longName, longName.toUpperCase(), "   ", "Utilizator", "\tAlisa\u00A0"};
        try (var insert = connection.prepareStatement("""
                INSERT INTO users (id, display_name, email, password_hash, role, created_at,
                    password_version, hide_predictions_until_kickoff, email_verified)
                VALUES (?, ?, ?, 'unchanged-hash', 'USER', ?, 4, TRUE, TRUE)
                """)) {
            for (int index = 0; index < names.length; index++) {
                insert.setObject(1, id(index + 1));
                insert.setString(2, names[index]);
                insert.setString(3, "migration" + index + "@example.test");
                insert.setObject(4, java.time.OffsetDateTime.parse("2025-01-01T00:00:00Z").plusDays(index));
                insert.executeUpdate();
            }
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO predictions (id, user_id, match_id, home_score, away_score, created_at, updated_at)
                    VALUES ('42000000-0000-0000-0000-000000000001', '41000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000040', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
        }
        var before = unchangedData(connection);
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();
        assertThat(unchangedData(connection)).isEqualTo(before);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id, display_name FROM users ORDER BY created_at, id")) {
            var actual = new LinkedHashMap<UUID, String>();
            while (rows.next()) actual.put(rows.getObject(1, UUID.class), rows.getString(2));
            assertThat(actual).containsExactly(
                    Map.entry(id(1), "Alisa"), Map.entry(id(2), "alisa 3"), Map.entry(id(3), "ALISA 4"),
                    Map.entry(id(4), "Alisa 2"), Map.entry(id(5), longName),
                    Map.entry(id(6), "X".repeat(62) + " 2"), Map.entry(id(7), "Utilizator 2"),
                    Map.entry(id(8), "Utilizator"), Map.entry(id(9), "Alisa 5"));
            assertThat(actual.values()).allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(64));
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM account_name_migration_audit")) {
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(6);
        }
        for (String duplicate : new String[] {"ALISA", " Alisa ", "\tALISA\u00A0"}) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users (id, display_name, email, password_hash, role, created_at)
                    VALUES (?, ?, 'duplicate@example.test', 'hash', 'USER', CURRENT_TIMESTAMP)
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, duplicate);
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private Map<String, String> unchangedData(Connection connection) throws Exception {
        var data = new LinkedHashMap<String, String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT id, email, password_hash, role, created_at, password_version,
                    hide_predictions_until_kickoff, email_verified FROM users ORDER BY id
                """)) {
            while (rows.next()) {
                var values = new java.util.ArrayList<String>();
                for (int index = 2; index <= 8; index++) values.add(rows.getString(index));
                data.put(rows.getString(1), values.toString());
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id, user_id, match_id, home_score, away_score FROM predictions ORDER BY id")) {
            while (rows.next()) data.put("prediction:" + rows.getString(1), rows.getString(2) + "/" + rows.getString(3) + "/" + rows.getInt(4) + "/" + rows.getInt(5));
        }
        return data;
    }

    private UUID id(int number) { return UUID.fromString("41000000-0000-0000-0000-" + String.format("%012d", number)); }
}

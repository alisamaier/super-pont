package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V12__sportstats_import extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        boolean postgres = context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL");
        try (var sql = context.getConnection().createStatement()) {
            for (String table : new String[] {"competitions", "seasons", "teams", "competition_phases", "rounds", "football_matches"}) {
                sql.execute("ALTER TABLE " + table + " ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL'");
                sql.execute("ALTER TABLE " + table + " ADD COLUMN external_id VARCHAR(256)");
                sql.execute("ALTER TABLE " + table + " ADD CONSTRAINT ck_" + table + "_source CHECK ((provider = 'LOCAL' AND external_id IS NULL) OR (provider = 'SPORTSTATS' AND external_id IS NOT NULL))");
                String scope = switch (table) {
                    case "seasons" -> "competition_id, ";
                    case "competition_phases" -> "season_id, ";
                    case "rounds" -> "phase_id, ";
                    default -> "";
                };
                sql.execute("ALTER TABLE " + table + " ADD CONSTRAINT uq_" + table + "_external UNIQUE (provider, " + scope + "external_id)");
            }
            // Preserve local name uniqueness; imported clubs are identified by provider codes, never demo names.
            sql.execute("ALTER TABLE teams DROP CONSTRAINT uq_team_name");
            sql.execute("ALTER TABLE teams ADD COLUMN local_name VARCHAR(120) GENERATED ALWAYS AS (CASE WHEN provider = 'LOCAL' THEN name ELSE NULL END)" + (postgres ? " STORED" : ""));
            sql.execute("ALTER TABLE teams ADD CONSTRAINT uq_team_local_name UNIQUE (country_id, local_name)");
            sql.execute("ALTER TABLE football_matches ADD COLUMN source_updated_at TIMESTAMP WITH TIME ZONE");
            sql.execute("ALTER TABLE football_matches DROP CONSTRAINT ck_match_status");
            sql.execute("ALTER TABLE football_matches ADD CONSTRAINT ck_match_status CHECK (status IN ('SCHEDULED','LIVE','FINISHED','POSTPONED','CANCELLED','SUSPENDED','UNKNOWN'))");
            sql.execute("ALTER TABLE football_matches DROP CONSTRAINT ck_result_status");
            sql.execute("ALTER TABLE football_matches ADD CONSTRAINT ck_result_status CHECK ((status = 'FINISHED' AND home_score IS NOT NULL AND away_score IS NOT NULL) OR status = 'LIVE' OR (status IN ('SCHEDULED','POSTPONED','CANCELLED','SUSPENDED','UNKNOWN') AND home_score IS NULL AND away_score IS NULL))");
            sql.execute("""
                    CREATE TABLE sportstats_sync_state (
                        provider VARCHAR(32) PRIMARY KEY,
                        lock_owner UUID,
                        locked_until TIMESTAMP WITH TIME ZONE,
                        retry_after TIMESTAMP WITH TIME ZONE
                    )
                    """);
            sql.execute("INSERT INTO sportstats_sync_state (provider) VALUES ('SPORTSTATS')");
        }
    }
}

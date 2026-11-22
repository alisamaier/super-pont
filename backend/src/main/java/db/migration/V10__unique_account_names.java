package db.migration;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Frozen normalization rules: keep this migration independent of application validators. */
public class V10__unique_account_names extends BaseJavaMigration {
    private static final String WHITESPACE = " \t\n\r\f\u000B\u001C\u001D\u001E\u001F\u0085\u00A0\u1680"
            + "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF";
    private static final Pattern EDGES = Pattern.compile("^[\\p{javaWhitespace}\\p{Z}\\u0085\\uFEFF]+|[\\p{javaWhitespace}\\p{Z}\\u0085\\uFEFF]+$");
    private record Account(UUID id, String name, String key) {}

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        boolean postgres = connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
        String keyExpression = "LOWER(TRIM(BOTH '" + WHITESPACE + "' FROM display_name))";
        // Block old application writers until names and the unique constraint are committed together.
        try (var sql = connection.createStatement()) {
            if (postgres) sql.execute("LOCK TABLE users IN ACCESS EXCLUSIVE MODE");
            sql.execute("""
                    CREATE TABLE account_name_migration_audit (
                        user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                        previous_name VARCHAR(64) NOT NULL,
                        new_name VARCHAR(64) NOT NULL,
                        changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        var accounts = new ArrayList<Account>();
        var reserved = new HashSet<String>();
        try (var sql = connection.createStatement();
                var rows = sql.executeQuery("SELECT id, display_name, " + keyExpression
                        + " AS name_key FROM users ORDER BY created_at, id")) {
            while (rows.next()) {
                var account = new Account(rows.getObject("id", UUID.class), rows.getString("display_name"), rows.getString("name_key"));
                accounts.add(account);
                reserved.add(account.key());
            }
        }

        var claimed = new HashSet<String>();
        try (var lower = connection.prepareStatement("SELECT LOWER(?)");
                var update = connection.prepareStatement("UPDATE users SET display_name = ? WHERE id = ?");
                var audit = connection.prepareStatement("INSERT INTO account_name_migration_audit (user_id, previous_name, new_name) VALUES (?, ?, ?)")) {
            for (var account : accounts) {
                String base = trim(account.name());
                if (base.isEmpty()) base = "Utilizator";
                String name = base;
                String key = lower(lower, name);
                if (claimed.contains(key) || (!key.equals(account.key()) && reserved.contains(key))) {
                    int suffix = 2;
                    do {
                        String ending = " " + suffix++;
                        int end = Math.min(base.length(), 64 - ending.length());
                        if (end > 0 && Character.isHighSurrogate(base.charAt(end - 1))) end--;
                        name = trim(base.substring(0, end)) + ending;
                        key = lower(lower, name);
                    } while (reserved.contains(key));
                }
                claimed.add(key);
                reserved.add(key);
                if (!Objects.equals(account.name(), name)) {
                    audit.setObject(1, account.id());
                    audit.setString(2, account.name());
                    audit.setString(3, name);
                    audit.executeUpdate();
                    update.setString(1, name);
                    update.setObject(2, account.id());
                    update.executeUpdate();
                }
            }
        }

        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE users ADD COLUMN display_name_key VARCHAR(128) GENERATED ALWAYS AS ("
                    + keyExpression + ")" + (postgres ? " STORED" : ""));
            sql.execute("ALTER TABLE users ALTER COLUMN display_name_key SET NOT NULL");
            sql.execute("ALTER TABLE users ADD CONSTRAINT uq_users_display_name_key UNIQUE (display_name_key)");
        }
    }

    private static String trim(String value) { return EDGES.matcher(value).replaceAll(""); }

    private static String lower(PreparedStatement statement, String name) throws Exception {
        statement.setString(1, name);
        try (var row = statement.executeQuery()) { row.next(); return row.getString(1); }
    }
}

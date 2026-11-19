package db.migration;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Validate all legacy accounts before changing data or constraints. */
public class V7__email_authentication extends BaseJavaMigration {
    private record EmailCandidate(@NotBlank @Email @Size(max = 254) String email) {}
    private record Account(UUID id, String email, String name) {}

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var accounts = new ArrayList<Account>();
        var conflicts = new ArrayList<String>();
        Map<String, List<UUID>> byEmail = new LinkedHashMap<>();
        try (var factory = Validation.buildDefaultValidatorFactory();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT id, email, display_name, username FROM users ORDER BY id")) {
            var validator = factory.getValidator();
            while (rows.next()) {
                UUID id = rows.getObject("id", UUID.class);
                String email = rows.getString("email");
                // Frozen migration equivalent of the registration/login email normalizer.
                email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
                if (!validator.validate(new EmailCandidate(email)).isEmpty()) {
                    conflicts.add("Missing or invalid email for account " + id);
                } else {
                    byEmail.computeIfAbsent(email, ignored -> new ArrayList<>()).add(id);
                }
                String name = rows.getString("display_name");
                if (name == null || name.isBlank()) name = rows.getString("username");
                if (name == null || name.isBlank()) name = "Utilizator";
                accounts.add(new Account(id, email, name));
            }
        }
        byEmail.values().stream().filter(ids -> ids.size() > 1)
                .forEach(ids -> conflicts.add("Duplicate normalized email for accounts " + ids));
        if (!conflicts.isEmpty()) throw new FlywayException("Email authentication migration requires resolution: " + String.join("; ", conflicts));

        try (var update = connection.prepareStatement(
                "UPDATE users SET email = ?, display_name = ?, password_version = password_version + 1 WHERE id = ?")) {
            for (var account : accounts) {
                update.setString(1, account.email());
                update.setString(2, account.name());
                update.setObject(3, account.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }
}

package ro.superpont.football.sportstats.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SportStatsSyncRepository {
    private final JdbcTemplate jdbc;
    public SportStatsSyncRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean acquire(UUID owner, Instant now) {
        return jdbc.update("""
                UPDATE sportstats_sync_state SET lock_owner = ?, locked_until = ?
                WHERE provider = 'SPORTSTATS' AND (locked_until IS NULL OR locked_until <= ?)
                  AND (retry_after IS NULL OR retry_after <= ?)
                """, owner, stamp(now.plusSeconds(900)), stamp(now), stamp(now)) == 1;
    }
    public Instant retryAfter() {
        return jdbc.query("SELECT retry_after FROM sportstats_sync_state WHERE provider = 'SPORTSTATS'",
                result -> { result.next(); var value = result.getTimestamp(1); return value == null ? null : value.toInstant(); });
    }
    public boolean renew(UUID owner, Instant now) {
        return jdbc.update("UPDATE sportstats_sync_state SET locked_until=? WHERE provider='SPORTSTATS' AND lock_owner=? AND locked_until>?",
                stamp(now.plusSeconds(900)), owner, stamp(now)) == 1;
    }
    public boolean guard(UUID owner, Instant now) {
        // Hold this row until the entire import commits, including if the lease time expires during DB work.
        return jdbc.query("SELECT lock_owner, locked_until FROM sportstats_sync_state WHERE provider = 'SPORTSTATS' FOR UPDATE",
                result -> result.next() && owner.equals(result.getObject(1, UUID.class))
                        && result.getTimestamp(2).toInstant().isAfter(now));
    }
    public void release(UUID owner, Instant retryAfter) {
        jdbc.update("UPDATE sportstats_sync_state SET lock_owner = NULL, locked_until = NULL, retry_after = ? WHERE provider = 'SPORTSTATS' AND lock_owner = ?",
                retryAfter == null ? null : stamp(retryAfter), owner);
    }
    private static Timestamp stamp(Instant value) { return Timestamp.from(value); }
}

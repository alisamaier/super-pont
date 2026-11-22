package ro.superpont.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import ro.superpont.common.BaseEntity;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public boolean usableAt(Instant now) { return usedAt == null && now.isBefore(expiresAt); }
    public void consume(Instant now) { usedAt = now; }
}

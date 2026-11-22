package ro.superpont.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import ro.superpont.common.BaseEntity;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(name = "display_name_key", nullable = false, length = 128, insertable = false, updatable = false)
    private String displayNameKey;

    @Column(nullable = false)
    private boolean hidePredictionsUntilKickoff;

    @Column(nullable = false)
    private long passwordVersion;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(String displayName, String email, String passwordHash) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.createdAt = Instant.now();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getPasswordVersion() {
        return passwordVersion;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordVersion++;
    }

    public boolean isHidePredictionsUntilKickoff() {
        return hidePredictionsUntilKickoff;
    }

    public void setHidePredictionsUntilKickoff(boolean hidePredictionsUntilKickoff) {
        this.hidePredictionsUntilKickoff = hidePredictionsUntilKickoff;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() { return emailVerified; }

    public void verifyEmail() { emailVerified = true; }

    public void correctEmail(String email) { this.email = email; }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

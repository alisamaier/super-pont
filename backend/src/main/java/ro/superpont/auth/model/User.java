package ro.superpont.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import ro.superpont.common.BaseEntity;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.createdAt = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

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

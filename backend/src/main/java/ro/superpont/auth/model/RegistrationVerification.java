package ro.superpont.auth.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registration_verifications")
public class RegistrationVerification {
    @Id private UUID userId;
    @Column(nullable = false, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant tokenExpiresAt;
    @Column(length = 60) private String codeHash;
    private Instant codeExpiresAt;
    @Column(nullable = false) private int failedAttempts;
    @Column(nullable = false) private Instant resendAvailableAt;
    @Column(nullable = false, length = 16) private String deliveryStatus;
    @Column(nullable = false) private Instant sendWindowStartedAt;
    @Column(nullable = false) private int sendsInWindow;

    protected RegistrationVerification() {}

    public RegistrationVerification(UUID userId, Instant now) {
        this.userId = userId;
        resendAvailableAt = now;
        sendWindowStartedAt = now;
        deliveryStatus = "NONE";
    }

    public void rotateToken(String hash, Instant now) {
        tokenHash = hash;
        tokenExpiresAt = now.plus(Duration.ofHours(24));
    }

    public void issue(String hash, Instant now, Duration window) {
        if (!now.isBefore(sendWindowStartedAt.plus(window))) {
            sendWindowStartedAt = now;
            sendsInWindow = 0;
        }
        sendsInWindow++;
        codeHash = hash;
        codeExpiresAt = now.plus(Duration.ofMinutes(10));
        failedAttempts = 0;
        resendAvailableAt = now.plusSeconds(60);
    }

    public void delivery(boolean sent) {
        deliveryStatus = sent ? "SENT" : "FAILED";
        if (!sent) { codeHash = null; codeExpiresAt = null; }
    }

    public void deferSending(Instant until) {
        resendAvailableAt = until;
        deliveryStatus = "RATE_LIMITED";
    }

    public void failAttempt() { failedAttempts++; }
    public void consumeCode() { codeHash = null; codeExpiresAt = null; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public String getCodeHash() { return codeHash; }
    public Instant getCodeExpiresAt() { return codeExpiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getResendAvailableAt() { return resendAvailableAt; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public Instant getSendWindowStartedAt() { return sendWindowStartedAt; }
    public int getSendsInWindow() { return sendsInWindow; }
}

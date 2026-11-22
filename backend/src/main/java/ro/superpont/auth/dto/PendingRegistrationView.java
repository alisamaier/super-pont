package ro.superpont.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record PendingRegistrationView(UUID id, String email, String state, Instant serverTime,
        Instant codeExpiresAt, Instant resendAvailableAt, int attemptsRemaining, String deliveryStatus) {}

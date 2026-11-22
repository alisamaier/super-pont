package ro.superpont.auth.dto;

import jakarta.validation.constraints.*;

public record PasswordResetRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {}

package ro.superpont.auth.dto;

import jakarta.validation.constraints.*;

public record PasswordResetTokenRequest(@NotBlank @Size(max = 128) String token) {}

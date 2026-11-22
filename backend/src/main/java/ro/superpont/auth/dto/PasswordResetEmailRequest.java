package ro.superpont.auth.dto;

import jakarta.validation.constraints.*;
import ro.superpont.auth.validation.EmailAddress;

public record PasswordResetEmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    public PasswordResetEmailRequest { email = EmailAddress.normalize(email); }
}

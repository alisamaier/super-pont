package ro.superpont.auth.dto;

import jakarta.validation.constraints.*;
import ro.superpont.auth.validation.EmailAddress;

public record CorrectRegistrationEmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    public CorrectRegistrationEmailRequest { email = EmailAddress.normalize(email); }
}

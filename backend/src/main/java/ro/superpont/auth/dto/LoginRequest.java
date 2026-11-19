package ro.superpont.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import ro.superpont.auth.validation.EmailAddress;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
    public LoginRequest { email = EmailAddress.normalize(email); }
}

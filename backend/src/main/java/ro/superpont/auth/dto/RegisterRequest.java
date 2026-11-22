package ro.superpont.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ro.superpont.auth.validation.EmailAddress;
import ro.superpont.auth.validation.DisplayName;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 64) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
    public RegisterRequest {
        displayName = DisplayName.normalize(displayName);
        email = EmailAddress.normalize(email);
    }
}

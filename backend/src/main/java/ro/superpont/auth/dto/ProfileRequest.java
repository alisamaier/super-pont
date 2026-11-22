package ro.superpont.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ro.superpont.auth.validation.DisplayName;

public record ProfileRequest(@NotBlank @Size(max = 64) String displayName) {
    public ProfileRequest {
        displayName = DisplayName.normalize(displayName);
    }
}

package ro.superpont.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(@NotBlank @Size(max = 64) String displayName) {
    public ProfileRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}

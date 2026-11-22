package ro.superpont.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConfirmRegistrationRequest(@NotNull @Pattern(regexp = "[0-9]{6}") String code) {}

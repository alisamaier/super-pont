package ro.superpont.football.competition.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record CompetitionRequest(
        @NotNull UUID countryId,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 120) String name) {}

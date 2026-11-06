package ro.superpont.football.team.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record TeamRequest(
        @NotNull UUID countryId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 50) String shortName) {}

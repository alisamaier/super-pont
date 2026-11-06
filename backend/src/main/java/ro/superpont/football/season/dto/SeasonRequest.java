package ro.superpont.football.season.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record SeasonRequest(
        @NotNull UUID competitionId,
        @NotBlank @Size(max = 50) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}

package ro.superpont.football.round.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record RoundRequest(
        @NotNull UUID phaseId,
        @NotNull @Min(1) Integer number,
        @NotBlank @Size(max = 100) String name) {}

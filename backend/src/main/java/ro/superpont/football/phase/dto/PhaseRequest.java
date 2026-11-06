package ro.superpont.football.phase.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.phase.model.PhaseFormat;

import java.time.*;
import java.util.*;

public record PhaseRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull PhaseFormat format,
        @NotNull @Min(1) Integer position,
        @NotNull @Min(0) Integer winPoints,
        @NotNull @Min(0) Integer drawPoints,
        @NotNull @Min(0) Integer lossPoints) {}

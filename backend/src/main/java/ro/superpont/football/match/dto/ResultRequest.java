package ro.superpont.football.match.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.match.model.MatchStatus;

import java.time.*;
import java.util.*;

public record ResultRequest(
        @NotNull MatchStatus status, @Min(0) Integer homeScore, @Min(0) Integer awayScore) {}

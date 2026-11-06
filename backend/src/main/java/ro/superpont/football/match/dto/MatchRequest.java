package ro.superpont.football.match.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.match.model.MatchStatus;

import java.time.*;
import java.util.*;

public record MatchRequest(
        @NotNull UUID roundId,
        @NotNull UUID homeTeamId,
        @NotNull UUID awayTeamId,
        @NotNull OffsetDateTime kickoff,
        @Size(max = 160) String venue,
        @NotNull MatchStatus status,
        @Min(0) Integer homeScore,
        @Min(0) Integer awayScore) {}

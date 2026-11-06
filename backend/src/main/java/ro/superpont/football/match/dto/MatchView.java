package ro.superpont.football.match.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.match.model.Match;
import ro.superpont.football.match.model.MatchStatus;
import ro.superpont.football.team.dto.TeamView;

import java.time.*;
import java.util.*;

public record MatchView(
        UUID id,
        UUID seasonId,
        UUID roundId,
        TeamView homeTeam,
        TeamView awayTeam,
        OffsetDateTime kickoff,
        String venue,
        MatchStatus status,
        Integer homeScore,
        Integer awayScore) {
    public static MatchView of(Match e) {
        return new MatchView(
                e.getId(),
                e.getSeason().getId(),
                e.getRound().getId(),
                TeamView.of(e.getHomeTeam()),
                TeamView.of(e.getAwayTeam()),
                e.getKickoff(),
                e.getVenue(),
                e.getStatus(),
                e.getHomeScore(),
                e.getAwayScore());
    }
}

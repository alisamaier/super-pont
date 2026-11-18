package ro.superpont.football.prediction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;
import ro.superpont.football.match.model.MatchStatus;

public record PublicPredictionView(
        UUID matchId, UUID roundId, int roundNumber, String roundName, String phaseName,
        String homeTeam, String awayTeam, OffsetDateTime kickoff, MatchStatus status,
        Visibility visibility,
        @JsonInclude(JsonInclude.Include.NON_NULL) Score predictedScore,
        PredictionView.Result result) {
    public enum Visibility { HIDDEN, REVEALED }
    public record Score(int home, int away) {}
}

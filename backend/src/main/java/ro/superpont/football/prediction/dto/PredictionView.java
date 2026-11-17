package ro.superpont.football.prediction.dto;

import java.time.Instant;
import java.util.UUID;
import ro.superpont.football.prediction.model.Prediction;

public record PredictionView(
        UUID id, UUID matchId, Integer homeScore, Integer awayScore, Instant createdAt, Instant updatedAt) {
    public static PredictionView of(Prediction prediction) {
        return new PredictionView(
                prediction.getId(),
                prediction.getMatch().getId(),
                prediction.getHomeScore(),
                prediction.getAwayScore(),
                prediction.getCreatedAt(),
                prediction.getUpdatedAt());
    }
}

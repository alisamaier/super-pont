package ro.superpont.football.prediction.service;

import java.time.Instant;
import java.time.OffsetDateTime;

final class PredictionDeadline {
    private PredictionDeadline() {}

    static boolean hasPassed(OffsetDateTime kickoff, Instant now) {
        return !now.isBefore(kickoff.toInstant());
    }
}

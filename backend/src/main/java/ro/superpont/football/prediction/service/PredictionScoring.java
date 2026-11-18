package ro.superpont.football.prediction.service;

import ro.superpont.football.match.model.MatchStatus;
import ro.superpont.football.prediction.dto.PredictionView;

final class PredictionScoring {
    private PredictionScoring() {}

    static PredictionView.Result evaluate(
            MatchStatus status, Integer home, Integer away, int predictedHome, int predictedAway) {
        if (status != MatchStatus.FINISHED || home == null || away == null) return null;

        return new PredictionView.Result(home, away, points(home, away, predictedHome, predictedAway));
    }

    static int points(int home, int away, int predictedHome, int predictedAway) {
        int outcomePoints = Integer.compare(predictedHome, predictedAway) == Integer.compare(home, away) ? 5 : 0;
        long totalError = Math.abs((long) predictedHome - home) + Math.abs((long) predictedAway - away);
        int accuracyBonus = (int) Math.max(0, 5 - totalError);
        return outcomePoints + accuracyBonus;
    }
}

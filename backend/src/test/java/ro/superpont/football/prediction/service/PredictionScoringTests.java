package ro.superpont.football.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ro.superpont.football.match.model.MatchStatus;
import ro.superpont.football.prediction.dto.PredictionView;

class PredictionScoringTests {
    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 0, 10",
        "0, 0, 1, 1, 8",
        "0, 0, 1, 0, 4",
        "1, 1, 0, 0, 8",
        "1, 0, 2, 0, 9",
        "1, 0, 0, 1, 3",
        "5, 0, 4, 0, 9",
        "5, 0, 3, 0, 8",
        "5, 0, 6, 1, 8",
        "5, 0, 1, 0, 6",
        "5, 0, 4, 4, 0",
        "1, 3, 0, 2, 8",
        "2147483647, 2147483647, 0, 0, 5"
    })
    void calculatesPointsForFinishedMatches(
            int home, int away, int predictedHome, int predictedAway, int points) {
        assertThat(PredictionScoring.evaluate(MatchStatus.FINISHED, home, away, predictedHome, predictedAway))
                .isEqualTo(new PredictionView.Result(home, away, points));
    }

    @ParameterizedTest
    @CsvSource({"LIVE, 1, 2", "LIVE, , ", "SCHEDULED, , ", "FINISHED, 0, ", "FINISHED, , 0"})
    void waitsForBothFinalScores(MatchStatus status, Integer home, Integer away) {
        assertThat(PredictionScoring.evaluate(status, home, away, 1, 2)).isNull();
    }
}

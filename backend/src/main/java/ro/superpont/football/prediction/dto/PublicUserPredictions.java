package ro.superpont.football.prediction.dto;

import java.time.Instant;
import java.util.UUID;
import ro.superpont.common.PageView;

public record PublicUserPredictions(
        String displayName, UUID competitionId, String competitionName,
        UUID seasonId, String seasonName, long totalPoints, Instant serverTime,
        PageView<PublicPredictionView> predictions) {}

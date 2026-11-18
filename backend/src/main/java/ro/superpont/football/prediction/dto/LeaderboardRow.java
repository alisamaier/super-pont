package ro.superpont.football.prediction.dto;

import java.util.UUID;

public record LeaderboardRow(int rank, UUID userId, String displayName, long totalPoints) {}

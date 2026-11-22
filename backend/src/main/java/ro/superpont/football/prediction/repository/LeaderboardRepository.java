package ro.superpont.football.prediction.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ro.superpont.auth.model.User;

public interface LeaderboardRepository extends Repository<User, UUID> {
    // Aggregate and rank by points alone before applying the display order and page.
    // BIGINT arithmetic keeps the accuracy calculation safe for large actual scores.
    @Query(value = """
            WITH points AS (
                SELECT p.user_id, SUM(
                    CASE WHEN SIGN(p.home_score - p.away_score) = SIGN(m.home_score - m.away_score)
                         THEN 5 ELSE 0 END
                    + GREATEST(CAST(0 AS BIGINT), 5
                        - ABS(CAST(p.home_score AS BIGINT) - m.home_score)
                        - ABS(CAST(p.away_score AS BIGINT) - m.away_score))
                ) AS total_points
                FROM predictions p
                JOIN football_matches m ON m.id = p.match_id
                JOIN seasons s ON s.id = m.season_id
                WHERE s.id = :seasonId AND s.competition_id = :competitionId
                  AND m.status = 'FINISHED' AND m.home_score IS NOT NULL AND m.away_score IS NOT NULL
                GROUP BY p.user_id
            ), totals AS (
                SELECT u.id AS user_id, u.display_name, COALESCE(p.total_points, 0) AS total_points
                FROM users u LEFT JOIN points p ON p.user_id = u.id
            ), ranked AS (
                SELECT RANK() OVER (ORDER BY total_points DESC) AS standing, user_id, display_name, total_points
                FROM totals
            )
            SELECT standing AS rank, CAST(user_id AS VARCHAR(36)) AS userId,
                   display_name AS displayName, total_points AS totalPoints
            FROM ranked
            ORDER BY total_points DESC, LOWER(display_name), user_id
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Standing> findStandings(@Param("competitionId") UUID competitionId, @Param("seasonId") UUID seasonId,
            @Param("size") int size, @Param("offset") long offset);

    long count();

    interface Standing {
        int getRank();
        String getUserId();
        String getDisplayName();
        long getTotalPoints();
    }
}

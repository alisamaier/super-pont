package ro.superpont.football.prediction.repository;

import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import ro.superpont.football.prediction.model.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    @EntityGraph(attributePaths = "match")
    Page<Prediction> findByUserId(UUID userId, Pageable pageable);

    Optional<Prediction> findByUserIdAndMatchId(UUID userId, UUID matchId);

    @EntityGraph(attributePaths = {"match.homeTeam", "match.awayTeam", "match.round.phase"})
    Page<Prediction> findByUserIdAndMatchSeasonIdAndMatchSeasonCompetitionId(
            UUID userId, UUID seasonId, UUID competitionId, Pageable pageable);

    @Query("""
            select u.id as userId, u.displayName as displayName,
                   p.homeScore as predictedHome, p.awayScore as predictedAway,
                   m.homeScore as finalHome, m.awayScore as finalAway
            from Prediction p join p.match m join p.user u
            where m.season.id = :seasonId and m.season.competition.id = :competitionId
              and (:userId is null or u.id = :userId) and m.status = FINISHED
              and m.homeScore is not null and m.awayScore is not null
            """)
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    Stream<EvaluatedPrediction> streamEvaluatedBySeasonId(
            @Param("seasonId") UUID seasonId, @Param("competitionId") UUID competitionId,
            @Param("userId") UUID userId);

    interface EvaluatedPrediction {
        UUID getUserId();
        String getDisplayName();
        int getPredictedHome();
        int getPredictedAway();
        int getFinalHome();
        int getFinalAway();
    }
}

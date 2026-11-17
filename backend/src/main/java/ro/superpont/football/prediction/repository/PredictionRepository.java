package ro.superpont.football.prediction.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ro.superpont.football.prediction.model.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    Page<Prediction> findByUserId(UUID userId, Pageable pageable);

    Optional<Prediction> findByUserIdAndMatchId(UUID userId, UUID matchId);
}

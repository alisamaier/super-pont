package ro.superpont.football.phase.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.phase.model.CompetitionPhase;

import java.util.*;

public interface CompetitionPhaseRepository extends JpaRepository<CompetitionPhase, UUID> {
    Optional<CompetitionPhase> findByProviderAndSeasonIdAndExternalId(String provider, UUID seasonId, String externalId);

    List<CompetitionPhase> findBySeasonIdOrderByPosition(UUID seasonId);
}

package ro.superpont.football.season.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.season.model.Season;

import java.util.*;

public interface SeasonRepository extends JpaRepository<Season, UUID> {
    Page<Season> findByCompetitionId(UUID competitionId, Pageable pageable);

    boolean existsByIdAndTeamsId(UUID id, UUID teamId);
}

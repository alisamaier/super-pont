package ro.superpont.football.round.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.round.model.Round;

import java.util.*;

public interface RoundRepository extends JpaRepository<Round, UUID> {
    Page<Round> findBySeasonId(UUID seasonId, Pageable pageable);
}

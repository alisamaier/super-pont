package ro.superpont.football.match.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.match.model.Match;

import java.util.*;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    Page<Match> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    Page<Match> findBySeasonId(UUID seasonId, Pageable pageable);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    Page<Match> findByRoundId(UUID roundId, Pageable pageable);
}

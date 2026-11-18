package ro.superpont.football.season.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.season.model.Season;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public interface SeasonRepository extends JpaRepository<Season, UUID> {
    Page<Season> findByCompetitionId(UUID competitionId, Pageable pageable);

    boolean existsByIdAndTeamsId(UUID id, UUID teamId);

    Optional<Season> findFirstByCompetitionCountryIsoCodeAndCompetitionCodeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDescIdAsc(
            String countryCode, String competitionCode, LocalDate latestStart, LocalDate earliestEnd);

    default Optional<Season> findCurrentSuperLiga(Instant now) {
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return findFirstByCompetitionCountryIsoCodeAndCompetitionCodeAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDescIdAsc(
                "RO", "RO-SUPERLIGA", today, today);
    }
}

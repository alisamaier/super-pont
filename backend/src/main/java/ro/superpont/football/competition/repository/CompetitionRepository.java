package ro.superpont.football.competition.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.competition.model.Competition;

import java.util.*;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {
    Page<Competition> findByCountryId(UUID countryId, Pageable pageable);
}

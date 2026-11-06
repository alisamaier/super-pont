package ro.superpont.football.team.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import ro.superpont.football.team.model.Team;

import java.util.*;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Page<Team> findByCountryId(UUID countryId, Pageable pageable);

    @Query("select t from Season s join s.teams t where s.id = :seasonId order by t.name")
    List<Team> findParticipants(@Param("seasonId") UUID seasonId);
}

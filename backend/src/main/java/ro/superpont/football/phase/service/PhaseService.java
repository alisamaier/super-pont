package ro.superpont.football.phase.service;

import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.football.phase.dto.PhaseRequest;
import ro.superpont.football.phase.dto.PhaseView;
import ro.superpont.football.phase.model.CompetitionPhase;
import ro.superpont.football.phase.repository.CompetitionPhaseRepository;
import ro.superpont.football.season.repository.SeasonRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class PhaseService {
    private final SeasonRepository seasons;
    private final CompetitionPhaseRepository phases;

    public PhaseService(SeasonRepository seasons, CompetitionPhaseRepository phases) {
        this.seasons = seasons;
        this.phases = phases;
    }

    public List<PhaseView> phases(UUID seasonId) {
        require(seasons, seasonId, "Season");
        return phases.findBySeasonIdOrderByPosition(seasonId).stream().map(PhaseView::of).toList();
    }

    public PhaseView phase(UUID id) {
        return PhaseView.of(require(phases, id, "Phase"));
    }

    @Transactional
    public PhaseView createPhase(UUID seasonId, PhaseRequest request) {
        var season = require(seasons, seasonId, "Season");
        return PhaseView.of(
                phases.saveAndFlush(
                        new CompetitionPhase(
                                season,
                                request.code().toUpperCase(Locale.ROOT),
                                request.name().strip(),
                                request.format(),
                                request.position(),
                                request.winPoints(),
                                request.drawPoints(),
                                request.lossPoints())));
    }
}

package ro.superpont.football.round.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.PageView;
import ro.superpont.football.phase.repository.CompetitionPhaseRepository;
import ro.superpont.football.round.dto.RoundRequest;
import ro.superpont.football.round.dto.RoundView;
import ro.superpont.football.round.model.Round;
import ro.superpont.football.round.repository.RoundRepository;
import ro.superpont.football.season.repository.SeasonRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RoundService {
    private final SeasonRepository seasons;
    private final CompetitionPhaseRepository phases;
    private final RoundRepository rounds;

    public RoundService(
            SeasonRepository seasons, CompetitionPhaseRepository phases, RoundRepository rounds) {
        this.seasons = seasons;
        this.phases = phases;
        this.rounds = rounds;
    }

    public PageView<RoundView> rounds(UUID seasonId, int page, int size) {
        var paging = page(page, size, "number");
        if (seasonId != null) require(seasons, seasonId, "Season");
        return PageView.of(
                (seasonId == null
                                ? rounds.findAll(paging)
                                : rounds.findBySeasonId(seasonId, paging))
                        .map(RoundView::of));
    }

    public RoundView round(UUID id) {
        return RoundView.of(require(rounds, id, "Round"));
    }

    @Transactional
    public RoundView createRound(RoundRequest request) {
        return RoundView.of(
                rounds.saveAndFlush(
                        new Round(
                                require(phases, request.phaseId(), "Phase"),
                                request.number(),
                                request.name().strip())));
    }
}

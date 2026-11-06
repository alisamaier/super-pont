package ro.superpont.football.season.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.DomainException;
import ro.superpont.common.PageView;
import ro.superpont.football.competition.repository.CompetitionRepository;
import ro.superpont.football.season.dto.ParticipantRequest;
import ro.superpont.football.season.dto.SeasonRequest;
import ro.superpont.football.season.dto.SeasonView;
import ro.superpont.football.season.model.Season;
import ro.superpont.football.season.repository.SeasonRepository;
import ro.superpont.football.team.dto.TeamView;
import ro.superpont.football.team.repository.TeamRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class SeasonService {
    private final CompetitionRepository competitions;
    private final TeamRepository teams;
    private final SeasonRepository seasons;

    public SeasonService(
            CompetitionRepository competitions, TeamRepository teams, SeasonRepository seasons) {
        this.competitions = competitions;
        this.teams = teams;
        this.seasons = seasons;
    }

    public PageView<SeasonView> seasons(UUID competitionId, int page, int size) {
        var paging = page(page, size, "startDate");
        if (competitionId != null) require(competitions, competitionId, "Competition");
        return PageView.of(
                (competitionId == null
                                ? seasons.findAll(paging)
                                : seasons.findByCompetitionId(competitionId, paging))
                        .map(SeasonView::of));
    }

    public SeasonView season(UUID id) {
        return SeasonView.of(require(seasons, id, "Season"));
    }

    @Transactional
    public SeasonView createSeason(SeasonRequest request) {
        if (request.endDate().isBefore(request.startDate()))
            throw DomainException.invalid("Season endDate must not precede startDate");
        var competition = require(competitions, request.competitionId(), "Competition");
        return SeasonView.of(
                seasons.saveAndFlush(
                        new Season(
                                competition,
                                request.name().strip(),
                                request.startDate(),
                                request.endDate())));
    }

    public List<TeamView> participants(UUID seasonId) {
        require(seasons, seasonId, "Season");
        return teams.findParticipants(seasonId).stream().map(TeamView::of).toList();
    }

    @Transactional
    public TeamView registerTeam(UUID seasonId, ParticipantRequest request) {
        var season = require(seasons, seasonId, "Season");
        var team = require(teams, request.teamId(), "Team");
        if (!seasons.existsByIdAndTeamsId(seasonId, team.getId())) {
            season.addTeam(team);
            seasons.saveAndFlush(season);
        }
        return TeamView.of(team);
    }
}

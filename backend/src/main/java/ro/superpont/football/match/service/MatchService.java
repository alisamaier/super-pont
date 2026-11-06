package ro.superpont.football.match.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.DomainException;
import ro.superpont.common.PageView;
import ro.superpont.football.match.dto.MatchRequest;
import ro.superpont.football.match.dto.MatchView;
import ro.superpont.football.match.dto.ResultRequest;
import ro.superpont.football.match.model.Match;
import ro.superpont.football.match.model.MatchStatus;
import ro.superpont.football.match.repository.MatchRepository;
import ro.superpont.football.round.repository.RoundRepository;
import ro.superpont.football.season.repository.SeasonRepository;
import ro.superpont.football.team.repository.TeamRepository;

import java.time.ZoneOffset;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MatchService {
    private final TeamRepository teams;
    private final SeasonRepository seasons;
    private final RoundRepository rounds;
    private final MatchRepository matches;

    public MatchService(
            TeamRepository teams,
            SeasonRepository seasons,
            RoundRepository rounds,
            MatchRepository matches) {
        this.teams = teams;
        this.seasons = seasons;
        this.rounds = rounds;
        this.matches = matches;
    }

    public PageView<MatchView> matches(UUID seasonId, UUID roundId, int page, int size) {
        var paging = page(page, size, "kickoff");
        if (seasonId != null) require(seasons, seasonId, "Season");
        if (roundId != null) {
            var round = require(rounds, roundId, "Round");
            if (seasonId != null && !seasonId.equals(round.getSeason().getId()))
                throw DomainException.invalid("Round does not belong to the requested season");
            return PageView.of(matches.findByRoundId(roundId, paging).map(MatchView::of));
        }
        return PageView.of(
                (seasonId == null
                                ? matches.findAll(paging)
                                : matches.findBySeasonId(seasonId, paging))
                        .map(MatchView::of));
    }

    public MatchView match(UUID id) {
        return MatchView.of(require(matches, id, "Match"));
    }

    @Transactional
    public MatchView createMatch(MatchRequest request) {
        if (request.homeTeamId().equals(request.awayTeamId()))
            throw DomainException.invalid("Home and away teams must differ");
        validateResult(request.status(), request.homeScore(), request.awayScore());
        var round = require(rounds, request.roundId(), "Round");
        var season = round.getSeason();
        var date = request.kickoff().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (date.isBefore(season.getStartDate()) || date.isAfter(season.getEndDate()))
            throw DomainException.invalid("Kickoff must be within the season dates (UTC)");
        var home = require(teams, request.homeTeamId(), "Home team");
        var away = require(teams, request.awayTeamId(), "Away team");
        if (!seasons.existsByIdAndTeamsId(season.getId(), home.getId())
                || !seasons.existsByIdAndTeamsId(season.getId(), away.getId())) {
            throw DomainException.invalid("Both teams must be registered in the match season");
        }
        return MatchView.of(
                matches.saveAndFlush(
                        new Match(
                                round,
                                home,
                                away,
                                request.kickoff(),
                                request.venue() == null ? null : request.venue().strip(),
                                request.status(),
                                request.homeScore(),
                                request.awayScore())));
    }

    @Transactional
    public MatchView updateResult(UUID id, ResultRequest request) {
        validateResult(request.status(), request.homeScore(), request.awayScore());
        var match = require(matches, id, "Match");
        match.updateResult(request.status(), request.homeScore(), request.awayScore());
        return MatchView.of(matches.saveAndFlush(match));
    }

    private static void validateResult(MatchStatus status, Integer home, Integer away) {
        if ((home == null) != (away == null))
            throw DomainException.invalid("Both scores must be supplied together");
        if ((home != null && home < 0) || (away != null && away < 0))
            throw DomainException.invalid("Actual match scores must be nonnegative integers");
        if (status == MatchStatus.FINISHED && home == null)
            throw DomainException.invalid("Finished matches require scores");
        if (status != MatchStatus.LIVE && status != MatchStatus.FINISHED && home != null)
            throw DomainException.invalid("Only live or finished matches can have scores");
    }
}

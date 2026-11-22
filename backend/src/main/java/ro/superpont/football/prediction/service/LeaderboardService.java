package ro.superpont.football.prediction.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.common.DomainException;
import ro.superpont.common.PageView;
import ro.superpont.football.prediction.dto.LeaderboardRow;
import ro.superpont.football.prediction.repository.LeaderboardRepository;
import ro.superpont.football.season.model.Season;
import ro.superpont.football.season.repository.SeasonRepository;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LeaderboardService {
    private final SeasonRepository seasons;
    private final LeaderboardRepository leaderboard;
    private final Clock clock;

    public LeaderboardService(SeasonRepository seasons, LeaderboardRepository leaderboard, Clock clock) {
        this.seasons = seasons;
        this.leaderboard = leaderboard;
        this.clock = clock;
    }

    public PageView<LeaderboardRow> currentSuperLiga(int pageNumber, int size) {
        var paging = page(pageNumber, size, "id");
        return seasons.findCurrentSuperLiga(clock.instant())
                .map(season -> standings(season, paging)).orElseGet(() -> PageView.of(Page.empty(paging)));
    }

    public PageView<LeaderboardRow> selectedSeason(UUID competitionId, UUID seasonId, int pageNumber, int size) {
        var paging = page(pageNumber, size, "id");
        var season = require(seasons, seasonId, "Season");
        if (!season.getCompetition().getId().equals(competitionId)) throw DomainException.missing("Season");
        return standings(season, paging);
    }

    private PageView<LeaderboardRow> standings(Season season, Pageable paging) {
        var rows = leaderboard.findStandings(season.getCompetition().getId(), season.getId(),
                paging.getPageSize(), paging.getOffset()).stream()
                .map(row -> new LeaderboardRow(row.getRank(), UUID.fromString(row.getUserId()), row.getDisplayName(), row.getTotalPoints()))
                .toList();
        return PageView.of(new PageImpl<>(rows, paging, leaderboard.count()));
    }
}

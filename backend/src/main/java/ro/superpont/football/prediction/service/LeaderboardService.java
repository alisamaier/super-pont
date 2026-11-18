package ro.superpont.football.prediction.service;

import static ro.superpont.common.RepositorySupport.page;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.common.PageView;
import ro.superpont.football.prediction.dto.LeaderboardRow;
import ro.superpont.football.prediction.repository.PredictionRepository;
import ro.superpont.football.season.repository.SeasonRepository;

@Service
@Transactional(readOnly = true)
public class LeaderboardService {
    private final SeasonRepository seasons;
    private final PredictionRepository predictions;
    private final Clock clock;

    public LeaderboardService(SeasonRepository seasons, PredictionRepository predictions, Clock clock) {
        this.seasons = seasons;
        this.predictions = predictions;
        this.clock = clock;
    }

    public PageView<LeaderboardRow> currentSuperLiga(int pageNumber, int size) {
        var paging = page(pageNumber, size, "totalPoints");
        var season = seasons.findCurrentSuperLiga(clock.instant());
        if (season.isEmpty()) return PageView.of(Page.empty(paging));

        Map<UUID, UserTotal> totals = new HashMap<>();
        try (var evaluated = predictions.streamEvaluatedBySeasonId(
                season.get().getId(), season.get().getCompetition().getId(), null)) {
            evaluated.forEach(prediction -> {
                long points = PredictionScoring.points(prediction.getFinalHome(), prediction.getFinalAway(),
                        prediction.getPredictedHome(), prediction.getPredictedAway());
                totals.merge(prediction.getUserId(), new UserTotal(prediction.getDisplayName(), points),
                        (current, next) -> new UserTotal(current.displayName(), current.points() + next.points()));
            });
        }

        var order = Comparator.comparingLong(UserTotal::points).reversed()
                .thenComparing(UserTotal::displayName, String.CASE_INSENSITIVE_ORDER);
        var sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<UUID, UserTotal>comparingByValue(order).thenComparing(Map.Entry.comparingByKey()))
                .toList();
        int start = (int) Math.min(paging.getOffset(), sorted.size());
        int end = (int) Math.min((long) start + size, sorted.size());
        var rows = new ArrayList<LeaderboardRow>(end - start);
        int rank = 0;
        long previousTotal = -1;
        for (int index = 0; index < end; index++) {
            var entry = sorted.get(index);
            var total = entry.getValue();
            if (total.points() != previousTotal) {
                rank = index + 1;
                previousTotal = total.points();
            }
            if (index >= start) {
                rows.add(new LeaderboardRow(rank, entry.getKey(), total.displayName(), total.points()));
            }
        }
        return PageView.of(new PageImpl<>(rows, paging, sorted.size()));
    }

    private record UserTotal(String displayName, long points) {}
}

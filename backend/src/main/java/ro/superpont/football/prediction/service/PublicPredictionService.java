package ro.superpont.football.prediction.service;

import static ro.superpont.common.RepositorySupport.page;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.common.DomainException;
import ro.superpont.common.PageView;
import ro.superpont.football.prediction.dto.PublicPredictionView;
import ro.superpont.football.prediction.dto.PublicUserPredictions;
import ro.superpont.football.prediction.model.Prediction;
import ro.superpont.football.prediction.repository.PredictionRepository;
import ro.superpont.football.season.repository.SeasonRepository;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PublicPredictionService {
    private final UserRepository users;
    private final SeasonRepository seasons;
    private final PredictionRepository predictions;
    private final Clock clock;

    public PublicPredictionService(UserRepository users, SeasonRepository seasons,
            PredictionRepository predictions, Clock clock) {
        this.users = users;
        this.seasons = seasons;
        this.predictions = predictions;
        this.clock = clock;
    }

    public PublicUserPredictions currentSuperLiga(UUID userId, int pageNumber, int size) {
        page(pageNumber, size, "id");
        var now = clock.instant();
        var displayName = users.findDisplayNameById(userId).orElseThrow(() -> DomainException.missing("User"));
        var season = seasons.findCurrentSuperLiga(now).orElseThrow(() -> DomainException.missing("Current SuperLiga season"));
        var competition = season.getCompetition();
        var sort = Sort.by("match.round.phase.position", "match.round.number").descending()
                .and(Sort.by("match.kickoff").descending()).and(Sort.by("id"));
        var selected = predictions.findByUserIdAndMatchSeasonIdAndMatchSeasonCompetitionId(
                userId, season.getId(), competition.getId(), PageRequest.of(pageNumber, size, sort));
        long total;
        try (var evaluated = predictions.streamEvaluatedBySeasonId(season.getId(), competition.getId(), userId)) {
            total = evaluated.mapToLong(prediction -> PredictionScoring.points(
                    prediction.getFinalHome(), prediction.getFinalAway(),
                    prediction.getPredictedHome(), prediction.getPredictedAway())).sum();
        }
        return new PublicUserPredictions(displayName, competition.getId(), competition.getName(),
                season.getId(), season.getName(), total, now, PageView.of(selected.map(prediction -> view(prediction, now))));
    }

    private static PublicPredictionView view(Prediction prediction, Instant now) {
        var match = prediction.getMatch();
        var round = match.getRound();
        boolean revealed = PredictionDeadline.hasPassed(match.getKickoff(), now);
        var score = revealed
                ? new PublicPredictionView.Score(prediction.getHomeScore(), prediction.getAwayScore()) : null;
        var result = revealed ? PredictionScoring.evaluate(match.getStatus(), match.getHomeScore(),
                match.getAwayScore(), prediction.getHomeScore(), prediction.getAwayScore()) : null;
        return new PublicPredictionView(match.getId(), round.getId(), round.getNumber(), round.getName(),
                round.getPhase().getName(), match.getHomeTeam().getName(), match.getAwayTeam().getName(),
                match.getKickoff(), match.getStatus(),
                revealed ? PublicPredictionView.Visibility.REVEALED : PublicPredictionView.Visibility.HIDDEN,
                score, result);
    }
}

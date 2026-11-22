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
import ro.superpont.football.season.model.Season;

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

    public PublicUserPredictions currentSuperLiga(UUID userId, int pageNumber, int size, UUID viewerId) {
        page(pageNumber, size, "id");
        var season = seasons.findCurrentSuperLiga(clock.instant()).orElseThrow(() -> DomainException.missing("Current SuperLiga season"));
        return predictions(userId, season, pageNumber, size, viewerId);
    }

    public PublicUserPredictions selectedSeason(UUID userId, UUID competitionId, UUID seasonId,
            int pageNumber, int size, UUID viewerId) {
        page(pageNumber, size, "id");
        var season = seasons.findById(seasonId).orElseThrow(() -> DomainException.missing("Season"));
        if (!season.getCompetition().getId().equals(competitionId)) throw DomainException.missing("Season");
        return predictions(userId, season, pageNumber, size, viewerId);
    }

    private PublicUserPredictions predictions(UUID userId, Season season, int pageNumber, int size, UUID viewerId) {
        var now = clock.instant();
        var owner = users.findById(userId).orElseThrow(() -> DomainException.missing("User"));
        boolean revealBeforeDeadline = !owner.isHidePredictionsUntilKickoff() || owner.getId().equals(viewerId);
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
        return new PublicUserPredictions(owner.getDisplayName(), competition.getId(), competition.getName(),
                season.getId(), season.getName(), total, now,
                PageView.of(selected.map(prediction -> view(prediction, now, revealBeforeDeadline))));
    }

    private static PublicPredictionView view(Prediction prediction, Instant now, boolean revealBeforeDeadline) {
        var match = prediction.getMatch();
        var round = match.getRound();
        boolean revealed = revealBeforeDeadline || PredictionDeadline.hasPassed(match.getKickoff(), now);
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

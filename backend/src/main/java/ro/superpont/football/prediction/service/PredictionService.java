package ro.superpont.football.prediction.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.common.DomainException;
import ro.superpont.common.PageView;
import ro.superpont.football.match.model.Match;
import ro.superpont.football.match.repository.MatchRepository;
import ro.superpont.football.prediction.dto.PredictionRequest;
import ro.superpont.football.prediction.dto.PredictionView;
import ro.superpont.football.prediction.model.Prediction;
import ro.superpont.football.prediction.repository.PredictionRepository;

@Service
@Transactional(readOnly = true)
public class PredictionService {
    private final UserRepository users;
    private final MatchRepository matches;
    private final PredictionRepository predictions;
    private final Clock clock;

    public PredictionService(UserRepository users, MatchRepository matches, PredictionRepository predictions,
            Clock clock) {
        this.users = users;
        this.matches = matches;
        this.predictions = predictions;
        this.clock = clock;
    }

    public PageView<PredictionView> currentPredictions(UUID userId, int pageNumber, int size) {
        var paging = page(pageNumber, size, "id");
        var user = users.findById(userId).orElseThrow(PredictionService::missingAccount);
        return PageView.of(predictions.findByUserId(user.getId(), paging).map(PredictionService::view));
    }

    @Transactional
    public SaveOutcome save(UUID userId, UUID matchId, PredictionRequest request) {
        // Lock a row that exists even for the first save, so concurrent PUT and DELETE see one order.
        var user = users.findForUpdateById(userId).orElseThrow(PredictionService::missingAccount);
        var match = require(matches, matchId, "Match");
        var existing = predictions.findByUserIdAndMatchId(user.getId(), matchId);
        var now = requireBeforeKickoff(match).truncatedTo(ChronoUnit.MICROS);
        if (existing.isPresent()) {
            var prediction = existing.get();
            prediction.update(request.homeScore(), request.awayScore(), now);
            return new SaveOutcome(view(prediction), false);
        }
        var prediction = predictions.save(
                new Prediction(user, match, request.homeScore(), request.awayScore(), now));
        return new SaveOutcome(view(prediction), true);
    }

    @Transactional
    public void delete(UUID userId, UUID matchId) {
        var user = users.findForUpdateById(userId).orElseThrow(PredictionService::missingAccount);
        var match = require(matches, matchId, "Match");
        var existing = predictions.findByUserIdAndMatchId(user.getId(), matchId);
        requireBeforeKickoff(match);
        existing.ifPresent(predictions::delete);
    }

    private Instant requireBeforeKickoff(Match match) {
        var now = clock.instant();
        if (PredictionDeadline.hasPassed(match.getKickoff(), now)) {
            throw new DomainException(HttpStatus.CONFLICT, "Predictions are locked at kickoff");
        }
        return now;
    }

    private static PredictionView view(Prediction prediction) {
        var match = prediction.getMatch();
        var result = PredictionScoring.evaluate(match.getStatus(), match.getHomeScore(), match.getAwayScore(),
                prediction.getHomeScore(), prediction.getAwayScore());
        return PredictionView.of(prediction, result);
    }

    private static DomainException missingAccount() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "Sign in required");
    }

    public record SaveOutcome(PredictionView prediction, boolean created) {}
}

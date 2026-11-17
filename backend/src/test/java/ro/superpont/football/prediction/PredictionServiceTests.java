package ro.superpont.football.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ro.superpont.auth.model.User;
import ro.superpont.auth.repository.UserRepository;
import ro.superpont.common.DomainException;
import ro.superpont.football.match.model.Match;
import ro.superpont.football.match.repository.MatchRepository;
import ro.superpont.football.prediction.dto.PredictionRequest;
import ro.superpont.football.prediction.model.Prediction;
import ro.superpont.football.prediction.repository.PredictionRepository;
import ro.superpont.football.prediction.service.PredictionService;

class PredictionServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final MatchRepository matches = mock(MatchRepository.class);
    private final PredictionRepository predictions = mock(PredictionRepository.class);
    private final Instant now = Instant.parse("2026-11-06T16:00:00Z");
    private final PredictionService service = new PredictionService(
            users, matches, predictions, Clock.fixed(now, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();
    private final User user = mock(User.class);
    private final Match match = mock(Match.class);

    PredictionServiceTests() {
        when(user.getId()).thenReturn(userId);
        when(match.getId()).thenReturn(matchId);
        when(users.findForUpdateByUsername("fan")).thenReturn(Optional.of(user));
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
    }

    @Test
    void retriesWithSameScoresKeepOriginalTimestamp() {
        when(match.getKickoff()).thenReturn(OffsetDateTime.parse("2026-11-06T18:00:01+02:00"));
        var createdAt = now.minusSeconds(1);
        var saved = new Prediction(user, match, 0, 99, createdAt);
        when(predictions.findByUserIdAndMatchId(userId, matchId)).thenReturn(Optional.of(saved));

        var outcome = service.save("fan", matchId, new PredictionRequest(0, 99));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.prediction().createdAt()).isEqualTo(createdAt);
        assertThat(outcome.prediction().updatedAt()).isEqualTo(createdAt);
        verify(predictions, never()).save(any());
    }

    @Test
    void cutoffUsesInstantEvenWhenKickoffHasDifferentOffset() {
        when(match.getKickoff()).thenReturn(OffsetDateTime.parse("2026-11-06T18:00:00+02:00"));
        var saved = new Prediction(user, match, 1, 2, now.minusSeconds(1));
        when(predictions.findByUserIdAndMatchId(userId, matchId)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.delete("fan", matchId))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(predictions, never()).delete(any());
    }
}

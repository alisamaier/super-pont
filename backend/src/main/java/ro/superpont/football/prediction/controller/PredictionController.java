package ro.superpont.football.prediction.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.superpont.common.PageView;
import ro.superpont.football.prediction.dto.PredictionRequest;
import ro.superpont.football.prediction.dto.PredictionView;
import ro.superpont.football.prediction.service.PredictionService;

@RestController
@RequestMapping("/api")
public class PredictionController {
    private final PredictionService predictions;

    public PredictionController(PredictionService predictions) {
        this.predictions = predictions;
    }

    @GetMapping("/predictions/me")
    public PageView<PredictionView> currentPredictions(Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return predictions.currentPredictions(UUID.fromString(principal.getName()), page, size);
    }

    @PutMapping("/matches/{matchId}/prediction")
    public ResponseEntity<PredictionView> save(Principal principal, @PathVariable UUID matchId,
            @Valid @RequestBody PredictionRequest request) {
        var result = predictions.save(UUID.fromString(principal.getName()), matchId, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/matches/" + matchId + "/prediction"))
                    .body(result.prediction());
        }
        return ResponseEntity.ok(result.prediction());
    }

    @DeleteMapping("/matches/{matchId}/prediction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable UUID matchId) {
        predictions.delete(UUID.fromString(principal.getName()), matchId);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail writeConflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Prediction is busy. Try again.");
    }
}

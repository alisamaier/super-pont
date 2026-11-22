package ro.superpont.football.prediction.controller;

import java.util.UUID;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.superpont.common.PageView;
import ro.superpont.football.prediction.dto.LeaderboardRow;
import ro.superpont.football.prediction.dto.PublicUserPredictions;
import ro.superpont.football.prediction.service.LeaderboardService;
import ro.superpont.football.prediction.service.PublicPredictionService;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboard;
    private final PublicPredictionService publicPredictions;

    public LeaderboardController(LeaderboardService leaderboard, PublicPredictionService publicPredictions) {
        this.leaderboard = leaderboard;
        this.publicPredictions = publicPredictions;
    }

    @GetMapping("/superliga")
    public ResponseEntity<PageView<LeaderboardRow>> currentSuperLiga(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(leaderboard.currentSuperLiga(page, size));
    }

    @GetMapping("/superliga/users/{userId}/predictions")
    public ResponseEntity<PublicUserPredictions> userPredictions(@PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(publicPredictions.currentSuperLiga(userId, page, size, UUID.fromString(principal.getName())));
    }

    @GetMapping
    public ResponseEntity<PageView<LeaderboardRow>> selectedSeason(
            @RequestParam UUID competitionId, @RequestParam UUID seasonId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(leaderboard.selectedSeason(competitionId, seasonId, page, size));
    }

    @GetMapping("/users/{userId}/predictions")
    public ResponseEntity<PublicUserPredictions> selectedUserPredictions(@PathVariable UUID userId,
            @RequestParam UUID competitionId, @RequestParam UUID seasonId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(publicPredictions.selectedSeason(userId, competitionId, seasonId, page, size,
                        UUID.fromString(principal.getName())));
    }
}

package ro.superpont.football.sportstats.controller;

import jakarta.validation.Valid;
import java.time.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ro.superpont.football.sportstats.client.SportStatsException;
import ro.superpont.football.sportstats.dto.*;
import ro.superpont.football.sportstats.service.SportStatsSyncService;

@RestController
@RequestMapping("/api/admin/sportstats")
public class SportStatsController {
    private final SportStatsSyncService sync;
    private final ro.superpont.football.sportstats.service.SportStatsLogoService logos;
    public SportStatsController(SportStatsSyncService sync, ro.superpont.football.sportstats.service.SportStatsLogoService logos) { this.sync = sync; this.logos = logos; }
    @PostMapping("/logos")
    public ResponseEntity<LogoSyncSummary> logos(@RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(logos.refresh(force));
    }
    @PostMapping("/sync")
    public ResponseEntity<SyncSummary> sync(@Valid @RequestBody SyncRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sync.sync(request.dateFrom(), request.dateTo()));
    }
    @ExceptionHandler(SportStatsException.class)
    public ResponseEntity<ProblemDetail> failed(SportStatsException exception) {
        var status = switch (exception.code()) {
            case "RETRY_AFTER", "API_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "API_KEY_MISSING" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "DATABASE_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, "Sincronizarea SportStats nu a fost finalizată. Datele salvate sunt păstrate.");
        problem.setProperty("code", exception.code());
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (exception.retryAfter() != null) response.header(HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1, Duration.between(Instant.now(), exception.retryAfter()).toSeconds() + 1)));
        return response.body(problem);
    }
}

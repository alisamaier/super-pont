package ro.superpont.football.match.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.match.dto.MatchRequest;
import ro.superpont.football.match.dto.MatchView;
import ro.superpont.football.match.dto.ResultRequest;
import ro.superpont.football.match.service.MatchService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MatchController {
    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @GetMapping("/matches")
    public PageView<MatchView> matches(
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(required = false) UUID roundId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.matches(seasonId, roundId, page, size);
    }

    @GetMapping("/matches/{id}")
    public MatchView match(@PathVariable UUID id) {
        return service.match(id);
    }

    @PostMapping("/matches")
    public ResponseEntity<MatchView> match(@Valid @RequestBody MatchRequest request) {
        var result = service.createMatch(request);
        return ResponseEntity.created(URI.create("/api/matches/" + result.id())).body(result);
    }

    @PutMapping("/matches/{id}/result")
    public MatchView result(@PathVariable UUID id, @Valid @RequestBody ResultRequest request) {
        return service.updateResult(id, request);
    }
}

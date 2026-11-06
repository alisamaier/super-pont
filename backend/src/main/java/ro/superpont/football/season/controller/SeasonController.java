package ro.superpont.football.season.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.season.dto.ParticipantRequest;
import ro.superpont.football.season.dto.SeasonRequest;
import ro.superpont.football.season.dto.SeasonView;
import ro.superpont.football.season.service.SeasonService;
import ro.superpont.football.team.dto.TeamView;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SeasonController {
    private final SeasonService service;

    public SeasonController(SeasonService service) {
        this.service = service;
    }

    @GetMapping("/seasons")
    public PageView<SeasonView> seasons(
            @RequestParam(required = false) UUID competitionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.seasons(competitionId, page, size);
    }

    @GetMapping("/seasons/{id}")
    public SeasonView season(@PathVariable UUID id) {
        return service.season(id);
    }

    @PostMapping("/seasons")
    public ResponseEntity<SeasonView> season(@Valid @RequestBody SeasonRequest request) {
        var result = service.createSeason(request);
        return ResponseEntity.created(URI.create("/api/seasons/" + result.id())).body(result);
    }

    @GetMapping("/seasons/{id}/teams")
    public List<TeamView> participants(@PathVariable UUID id) {
        return service.participants(id);
    }

    @PostMapping("/seasons/{id}/teams")
    public TeamView participant(
            @PathVariable UUID id, @Valid @RequestBody ParticipantRequest request) {
        return service.registerTeam(id, request);
    }
}

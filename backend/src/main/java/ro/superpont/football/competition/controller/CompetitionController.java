package ro.superpont.football.competition.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.competition.dto.CompetitionRequest;
import ro.superpont.football.competition.dto.CompetitionView;
import ro.superpont.football.competition.service.CompetitionService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CompetitionController {
    private final CompetitionService service;

    public CompetitionController(CompetitionService service) {
        this.service = service;
    }

    @GetMapping("/competitions")
    public PageView<CompetitionView> competitions(
            @RequestParam(required = false) UUID countryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.competitions(countryId, page, size);
    }

    @GetMapping("/competitions/{id}")
    public CompetitionView competition(@PathVariable UUID id) {
        return service.competition(id);
    }

    @PostMapping("/competitions")
    public ResponseEntity<CompetitionView> competition(
            @Valid @RequestBody CompetitionRequest request) {
        var result = service.createCompetition(request);
        return ResponseEntity.created(URI.create("/api/competitions/" + result.id())).body(result);
    }
}

package ro.superpont.football.round.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.round.dto.RoundRequest;
import ro.superpont.football.round.dto.RoundView;
import ro.superpont.football.round.service.RoundService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RoundController {
    private final RoundService service;

    public RoundController(RoundService service) {
        this.service = service;
    }

    @GetMapping("/rounds")
    public PageView<RoundView> rounds(
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.rounds(seasonId, page, size);
    }

    @GetMapping("/rounds/{id}")
    public RoundView round(@PathVariable UUID id) {
        return service.round(id);
    }

    @PostMapping("/rounds")
    public ResponseEntity<RoundView> round(@Valid @RequestBody RoundRequest request) {
        var result = service.createRound(request);
        return ResponseEntity.created(URI.create("/api/rounds/" + result.id())).body(result);
    }
}

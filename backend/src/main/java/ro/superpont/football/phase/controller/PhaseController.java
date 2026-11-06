package ro.superpont.football.phase.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.football.phase.dto.PhaseRequest;
import ro.superpont.football.phase.dto.PhaseView;
import ro.superpont.football.phase.service.PhaseService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PhaseController {
    private final PhaseService service;

    public PhaseController(PhaseService service) {
        this.service = service;
    }

    @GetMapping("/seasons/{id}/phases")
    public List<PhaseView> phases(@PathVariable UUID id) {
        return service.phases(id);
    }

    @PostMapping("/seasons/{id}/phases")
    public ResponseEntity<PhaseView> phase(
            @PathVariable UUID id, @Valid @RequestBody PhaseRequest request) {
        var result = service.createPhase(id, request);
        return ResponseEntity.created(URI.create("/api/phases/" + result.id())).body(result);
    }

    @GetMapping("/phases/{id}")
    public PhaseView phase(@PathVariable UUID id) {
        return service.phase(id);
    }
}

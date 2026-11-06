package ro.superpont.football.team.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.team.dto.TeamRequest;
import ro.superpont.football.team.dto.TeamView;
import ro.superpont.football.team.service.TeamService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TeamController {
    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping("/teams")
    public PageView<TeamView> teams(
            @RequestParam(required = false) UUID countryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.teams(countryId, page, size);
    }

    @GetMapping("/teams/{id}")
    public TeamView team(@PathVariable UUID id) {
        return service.team(id);
    }

    @PostMapping("/teams")
    public ResponseEntity<TeamView> team(@Valid @RequestBody TeamRequest request) {
        var result = service.createTeam(request);
        return ResponseEntity.created(URI.create("/api/teams/" + result.id())).body(result);
    }
}

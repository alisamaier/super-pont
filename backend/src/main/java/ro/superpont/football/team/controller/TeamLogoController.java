package ro.superpont.football.team.controller;

import java.util.UUID;
import java.time.Duration;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ro.superpont.football.team.service.TeamLogoReadService;

@RestController
public class TeamLogoController {
    private final TeamLogoReadService logos;
    public TeamLogoController(TeamLogoReadService logos) { this.logos = logos; }

    @GetMapping("/api/teams/{id}/logo")
    public ResponseEntity<Resource> logo(@PathVariable UUID id) {
        var logo = logos.get(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .eTag("\"" + logo.hash() + "\"").header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(logo.png()));
    }
}

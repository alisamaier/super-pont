package ro.superpont.football.country.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ro.superpont.common.PageView;
import ro.superpont.football.country.dto.CountryRequest;
import ro.superpont.football.country.dto.CountryView;
import ro.superpont.football.country.service.CountryService;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CountryController {
    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    @GetMapping("/countries")
    public PageView<CountryView> countries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.countries(page, size);
    }

    @GetMapping("/countries/{id}")
    public CountryView country(@PathVariable UUID id) {
        return service.country(id);
    }

    @PostMapping("/countries")
    public ResponseEntity<CountryView> country(@Valid @RequestBody CountryRequest request) {
        var result = service.createCountry(request);
        return ResponseEntity.created(URI.create("/api/countries/" + result.id())).body(result);
    }
}

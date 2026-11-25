package ro.superpont.football.sportstats.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SyncSummary(LocalDate dateFrom, LocalDate dateTo, int pages, int created, int updated,
        int ignored, int errors, List<String> warnings, Instant completedAt) {}

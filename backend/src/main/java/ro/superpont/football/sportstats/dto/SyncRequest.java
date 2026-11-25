package ro.superpont.football.sportstats.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SyncRequest(@NotNull LocalDate dateFrom, @NotNull LocalDate dateTo) {}

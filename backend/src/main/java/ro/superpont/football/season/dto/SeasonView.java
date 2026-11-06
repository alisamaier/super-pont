package ro.superpont.football.season.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.season.model.Season;

import java.time.*;
import java.util.*;

public record SeasonView(
        UUID id, UUID competitionId, String name, LocalDate startDate, LocalDate endDate) {
    public static SeasonView of(Season e) {
        return new SeasonView(
                e.getId(),
                e.getCompetition().getId(),
                e.getName(),
                e.getStartDate(),
                e.getEndDate());
    }
}

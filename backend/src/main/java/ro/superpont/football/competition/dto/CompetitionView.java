package ro.superpont.football.competition.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.competition.model.Competition;

import java.time.*;
import java.util.*;

public record CompetitionView(UUID id, UUID countryId, String code, String name) {
    public static CompetitionView of(Competition e) {
        return new CompetitionView(e.getId(), e.getCountry().getId(), e.getCode(), e.getName());
    }
}

package ro.superpont.football.team.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.team.model.Team;

import java.time.*;
import java.util.*;

public record TeamView(UUID id, UUID countryId, String name, String shortName) {
    public static TeamView of(Team e) {
        return new TeamView(e.getId(), e.getCountry().getId(), e.getName(), e.getShortName());
    }
}

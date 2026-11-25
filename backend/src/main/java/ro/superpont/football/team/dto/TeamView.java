package ro.superpont.football.team.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.team.model.Team;

import java.time.*;
import java.util.*;

public record TeamView(UUID id, UUID countryId, String name, String shortName, String logoUrl, String logoCredit) {
    public static TeamView of(Team e) {
        return new TeamView(e.getId(), e.getCountry().getId(), e.getName(), e.getShortName(),
                e.getLogoHash() == null ? null : "/api/teams/" + e.getId() + "/logo?v=" + e.getLogoHash(), e.getLogoCredit());
    }
}

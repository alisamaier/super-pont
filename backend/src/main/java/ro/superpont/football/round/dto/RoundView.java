package ro.superpont.football.round.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.round.model.Round;

import java.time.*;
import java.util.*;

public record RoundView(UUID id, UUID seasonId, UUID phaseId, int number, String name) {
    public static RoundView of(Round e) {
        return new RoundView(
                e.getId(), e.getSeason().getId(), e.getPhase().getId(), e.getNumber(), e.getName());
    }
}

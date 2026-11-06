package ro.superpont.football.phase.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.phase.model.CompetitionPhase;
import ro.superpont.football.phase.model.PhaseFormat;

import java.time.*;
import java.util.*;

public record PhaseView(
        UUID id,
        UUID seasonId,
        String code,
        String name,
        PhaseFormat format,
        int position,
        int winPoints,
        int drawPoints,
        int lossPoints) {
    public static PhaseView of(CompetitionPhase e) {
        return new PhaseView(
                e.getId(),
                e.getSeason().getId(),
                e.getCode(),
                e.getName(),
                e.getFormat(),
                e.getPosition(),
                e.getWinPoints(),
                e.getDrawPoints(),
                e.getLossPoints());
    }
}

package ro.superpont.football.round.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.phase.model.CompetitionPhase;
import ro.superpont.football.season.model.Season;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "rounds")
public class Round extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false)
    private CompetitionPhase phase;

    @Column(name = "round_number", nullable = false)
    private int number;

    @Column(nullable = false, length = 100)
    private String name;

    protected Round() {}

    public Round(CompetitionPhase phase, int number, String name) {
        this.phase = phase;
        this.number = number;
        this.name = name;
        this.season = phase.getSeason();
    }

    public CompetitionPhase getPhase() {
        return phase;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    public Season getSeason() {
        return season;
    }
}

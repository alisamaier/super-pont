package ro.superpont.football.phase.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.season.model.Season;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "competition_phases")
public class CompetitionPhase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhaseFormat format;

    @Column(nullable = false)
    private int position;

    @Column(name = "win_points", nullable = false)
    private int winPoints;

    @Column(name = "draw_points", nullable = false)
    private int drawPoints;

    @Column(name = "loss_points", nullable = false)
    private int lossPoints;

    protected CompetitionPhase() {}

    public CompetitionPhase(
            Season season,
            String code,
            String name,
            PhaseFormat format,
            int position,
            int winPoints,
            int drawPoints,
            int lossPoints) {
        this.season = season;
        this.code = code;
        this.name = name;
        this.format = format;
        this.position = position;
        this.winPoints = winPoints;
        this.drawPoints = drawPoints;
        this.lossPoints = lossPoints;
    }

    public Season getSeason() {
        return season;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PhaseFormat getFormat() {
        return format;
    }

    public int getPosition() {
        return position;
    }

    public int getWinPoints() {
        return winPoints;
    }

    public int getDrawPoints() {
        return drawPoints;
    }

    public int getLossPoints() {
        return lossPoints;
    }
}

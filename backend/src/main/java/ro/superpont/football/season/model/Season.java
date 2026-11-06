package ro.superpont.football.season.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.competition.model.Competition;
import ro.superpont.football.team.model.Team;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "seasons")
public class Season extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected Season() {}

    public Season(Competition competition, String name, LocalDate startDate, LocalDate endDate) {
        this.competition = competition;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Competition getCompetition() {
        return competition;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @ManyToMany
    @JoinTable(
            name = "season_teams",
            joinColumns = @JoinColumn(name = "season_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    private Set<Team> teams = new HashSet<>();

    public void addTeam(Team team) {
        teams.add(team);
    }
}

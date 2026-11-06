package ro.superpont.football.match.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.round.model.Round;
import ro.superpont.football.season.model.Season;
import ro.superpont.football.team.model.Team;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "football_matches")
public class Match extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(nullable = false)
    private OffsetDateTime kickoff;

    @Column(length = 160)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    protected Match() {}

    public Match(
            Round round,
            Team homeTeam,
            Team awayTeam,
            OffsetDateTime kickoff,
            String venue,
            MatchStatus status,
            Integer homeScore,
            Integer awayScore) {
        this.round = round;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoff = kickoff;
        this.venue = venue;
        this.status = status;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.season = round.getSeason();
    }

    public Round getRound() {
        return round;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public OffsetDateTime getKickoff() {
        return kickoff;
    }

    public String getVenue() {
        return venue;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    public Season getSeason() {
        return season;
    }

    public void updateResult(MatchStatus status, Integer homeScore, Integer awayScore) {
        this.status = status;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }
}

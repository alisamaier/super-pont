package ro.superpont.football.prediction.model;

import jakarta.persistence.*;
import java.time.Instant;
import ro.superpont.auth.model.User;
import ro.superpont.common.BaseEntity;
import ro.superpont.football.match.model.Match;

@Entity
@Table(name = "predictions")
public class Prediction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false)
    private Match match;

    @Column(nullable = false)
    private Integer homeScore;

    @Column(nullable = false)
    private Integer awayScore;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Prediction() {}

    public Prediction(User user, Match match, Integer homeScore, Integer awayScore, Instant now) {
        this.user = user;
        this.match = match;
        this.createdAt = now;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.updatedAt = now;
    }

    public void update(Integer homeScore, Integer awayScore, Instant now) {
        if (this.homeScore.equals(homeScore) && this.awayScore.equals(awayScore)) return;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.updatedAt = now;
    }

    public Match getMatch() {
        return match;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

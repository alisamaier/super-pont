package ro.superpont.football.team.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.country.model.Country;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "teams")
public class Team extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    protected Team() {}

    public Team(Country country, String name, String shortName) {
        this.country = country;
        this.name = name;
        this.shortName = shortName;
    }

    public Country getCountry() {
        return country;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }
}

package ro.superpont.football.competition.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;
import ro.superpont.football.country.model.Country;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "competitions")
public class Competition extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    protected Competition() {}

    public Competition(Country country, String code, String name) {
        this.country = country;
        this.code = code;
        this.name = name;
    }

    public Country getCountry() {
        return country;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}

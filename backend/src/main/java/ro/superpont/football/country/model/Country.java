package ro.superpont.football.country.model;

import jakarta.persistence.*;

import ro.superpont.common.BaseEntity;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "countries")
public class Country extends BaseEntity {
    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "iso_code", nullable = false, length = 2)
    private String isoCode;

    protected Country() {}

    public Country(String name, String isoCode) {
        this.name = name;
        this.isoCode = isoCode;
    }

    public String getName() {
        return name;
    }

    public String getIsoCode() {
        return isoCode;
    }
}

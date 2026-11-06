package ro.superpont.football.country.dto;

import jakarta.validation.constraints.*;

import ro.superpont.football.country.model.Country;

import java.time.*;
import java.util.*;

public record CountryView(UUID id, String name, String isoCode) {
    public static CountryView of(Country e) {
        return new CountryView(e.getId(), e.getName(), e.getIsoCode());
    }
}

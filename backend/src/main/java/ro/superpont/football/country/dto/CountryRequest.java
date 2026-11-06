package ro.superpont.football.country.dto;

import jakarta.validation.constraints.*;

import java.time.*;
import java.util.*;

public record CountryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Pattern(regexp = "[A-Za-z]{2}") String isoCode) {}

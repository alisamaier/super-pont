package ro.superpont.football.country.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import ro.superpont.football.country.model.Country;

import java.util.*;

public interface CountryRepository extends JpaRepository<Country, UUID> {
    Optional<Country> findByIsoCode(String isoCode);

    boolean existsByIsoCode(String isoCode);
}

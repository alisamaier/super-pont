package ro.superpont.football.country.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.PageView;
import ro.superpont.football.country.dto.CountryRequest;
import ro.superpont.football.country.dto.CountryView;
import ro.superpont.football.country.model.Country;
import ro.superpont.football.country.repository.CountryRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class CountryService {
    private final CountryRepository countries;

    public CountryService(CountryRepository countries) {
        this.countries = countries;
    }

    public PageView<CountryView> countries(int page, int size) {
        return PageView.of(countries.findAll(page(page, size, "name")).map(CountryView::of));
    }

    public CountryView country(UUID id) {
        return CountryView.of(require(countries, id, "Country"));
    }

    @Transactional
    public CountryView createCountry(CountryRequest request) {
        return CountryView.of(
                countries.saveAndFlush(
                        new Country(
                                request.name().strip(),
                                request.isoCode().toUpperCase(Locale.ROOT))));
    }
}

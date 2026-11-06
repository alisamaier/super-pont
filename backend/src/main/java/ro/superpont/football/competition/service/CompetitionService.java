package ro.superpont.football.competition.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.PageView;
import ro.superpont.football.competition.dto.CompetitionRequest;
import ro.superpont.football.competition.dto.CompetitionView;
import ro.superpont.football.competition.model.Competition;
import ro.superpont.football.competition.repository.CompetitionRepository;
import ro.superpont.football.country.repository.CountryRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class CompetitionService {
    private final CountryRepository countries;
    private final CompetitionRepository competitions;

    public CompetitionService(CountryRepository countries, CompetitionRepository competitions) {
        this.countries = countries;
        this.competitions = competitions;
    }

    public PageView<CompetitionView> competitions(UUID countryId, int page, int size) {
        var paging = page(page, size, "name");
        if (countryId != null) require(countries, countryId, "Country");
        return PageView.of(
                (countryId == null
                                ? competitions.findAll(paging)
                                : competitions.findByCountryId(countryId, paging))
                        .map(CompetitionView::of));
    }

    public CompetitionView competition(UUID id) {
        return CompetitionView.of(require(competitions, id, "Competition"));
    }

    @Transactional
    public CompetitionView createCompetition(CompetitionRequest request) {
        var country = require(countries, request.countryId(), "Country");
        return CompetitionView.of(
                competitions.saveAndFlush(
                        new Competition(
                                country,
                                request.code().toUpperCase(Locale.ROOT),
                                request.name().strip())));
    }
}

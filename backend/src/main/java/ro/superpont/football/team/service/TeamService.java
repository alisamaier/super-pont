package ro.superpont.football.team.service;

import static ro.superpont.common.RepositorySupport.page;
import static ro.superpont.common.RepositorySupport.require;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.superpont.common.PageView;
import ro.superpont.football.country.repository.CountryRepository;
import ro.superpont.football.team.dto.TeamRequest;
import ro.superpont.football.team.dto.TeamView;
import ro.superpont.football.team.model.Team;
import ro.superpont.football.team.repository.TeamRepository;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TeamService {
    private final CountryRepository countries;
    private final TeamRepository teams;

    public TeamService(CountryRepository countries, TeamRepository teams) {
        this.countries = countries;
        this.teams = teams;
    }

    public PageView<TeamView> teams(UUID countryId, int page, int size) {
        var paging = page(page, size, "name");
        if (countryId != null) require(countries, countryId, "Country");
        return PageView.of(
                (countryId == null
                                ? teams.findAll(paging)
                                : teams.findByCountryId(countryId, paging))
                        .map(TeamView::of));
    }

    public TeamView team(UUID id) {
        return TeamView.of(require(teams, id, "Team"));
    }

    @Transactional
    public TeamView createTeam(TeamRequest request) {
        var country = require(countries, request.countryId(), "Country");
        return TeamView.of(
                teams.saveAndFlush(
                        new Team(country, request.name().strip(), request.shortName().strip())));
    }
}

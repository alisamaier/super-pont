package ro.superpont.football.sportstats.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ro.superpont.common.DomainException;
import ro.superpont.football.competition.model.Competition;
import ro.superpont.football.competition.repository.CompetitionRepository;
import ro.superpont.football.country.model.Country;
import ro.superpont.football.country.repository.CountryRepository;
import ro.superpont.football.match.model.Match;
import ro.superpont.football.match.repository.MatchRepository;
import ro.superpont.football.phase.model.*;
import ro.superpont.football.phase.repository.CompetitionPhaseRepository;
import ro.superpont.football.round.model.Round;
import ro.superpont.football.round.repository.RoundRepository;
import ro.superpont.football.season.model.Season;
import ro.superpont.football.season.repository.SeasonRepository;
import ro.superpont.football.team.model.Team;
import ro.superpont.football.team.repository.TeamRepository;
import ro.superpont.football.sportstats.client.*;
import ro.superpont.football.sportstats.dto.SyncSummary;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import ro.superpont.football.sportstats.repository.SportStatsSyncRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SportStatsSyncService {
    private static final String PROVIDER = "SPORTSTATS";
    private static final Logger log = LoggerFactory.getLogger(SportStatsSyncService.class);
    private final SportStatsClient client;
    private final SportStatsSyncRepository locks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SportStatsProperties properties;
    private final SportStatsMatchMapper mapper;
    private final CountryRepository countries;
    private final CompetitionRepository competitions;
    private final SeasonRepository seasons;
    private final CompetitionPhaseRepository phases;
    private final RoundRepository rounds;
    private final TeamRepository teams;
    private final MatchRepository matches;

    public SportStatsSyncService(SportStatsClient client, SportStatsSyncRepository locks, TransactionTemplate transactions,
            Clock clock, SportStatsProperties properties, JsonMapper json, CountryRepository countries,
            CompetitionRepository competitions, SeasonRepository seasons, CompetitionPhaseRepository phases,
            RoundRepository rounds, TeamRepository teams, MatchRepository matches) {
        this.client = client; this.locks = locks; this.transactions = transactions; this.clock = clock;
        this.properties = properties; this.mapper = new SportStatsMatchMapper(properties, json);
        this.countries = countries; this.competitions = competitions; this.seasons = seasons;
        this.phases = phases; this.rounds = rounds; this.teams = teams; this.matches = matches;
    }

    public SyncSummary sync(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366)
            throw DomainException.invalid("Intervalul de sincronizare trebuie să fie de cel mult 367 de zile, în ordine cronologică.");
        var owner = UUID.randomUUID();
        boolean acquired = Boolean.TRUE.equals(transactions.execute(status -> locks.acquire(owner, clock.instant())));
        if (!acquired) {
            var retry = locks.retryAfter();
            if (retry != null && retry.isAfter(clock.instant())) throw new SportStatsException("RETRY_AFTER", retry);
            throw new DomainException(HttpStatus.CONFLICT, "O sincronizare SportStats este deja în desfășurare.");
        }
        Instant retry = null;
        try {
            // Fetch every page before touching football data. A failed later page preserves the previous snapshot.
            var batch = client.fetch(from, to);
            var summary = transactions.execute(status -> {
                if (!locks.guard(owner, clock.instant())) throw new SportStatsException("SYNC_LEASE_EXPIRED");
                return importBatch(batch, from, to);
            });
            log.info("SportStats sync created={} updated={} ignored={} errors={} pages={}", summary.created(), summary.updated(), summary.ignored(), summary.errors(), summary.pages());
            return summary;
        } catch (SportStatsException exception) {
            retry = exception.retryAfter() == null ? clock.instant().plusSeconds(30) : exception.retryAfter();
            log.warn("SportStats sync created=0 updated=0 ignored=0 errors=1 code={}", exception.code());
            throw exception;
        } catch (DataAccessException exception) {
            retry = clock.instant().plusSeconds(30);
            log.warn("SportStats sync created=0 updated=0 ignored=0 errors=1 code=DATABASE_CONFLICT");
            throw new SportStatsException("DATABASE_CONFLICT");
        } catch (RuntimeException exception) {
            retry = clock.instant().plusSeconds(30);
            log.warn("SportStats sync created=0 updated=0 ignored=0 errors=1 code=IMPORT_FAILED");
            throw new SportStatsException("IMPORT_FAILED");
        } finally {
            Instant backoff = retry;
            transactions.executeWithoutResult(status -> locks.release(owner, backoff));
        }
    }

    private SyncSummary importBatch(SportStatsClient.Batch batch, LocalDate from, LocalDate to) {
        int created = 0, updated = 0, ignored = 0, errors = 0;
        var warnings = new TreeSet<String>();
        for (var row : batch.matches()) {
            SportStatsMatchMapper.Planned plan;
            Optional<Match> existing;
            try {
                existing = matches.findByProviderAndExternalId(PROVIDER, mapper.matchCode(row));
                plan = mapper.map(row, existing.map(Match::getKickoff).orElse(null));
            }
            catch (SportStatsException invalid) { ignored++; errors++; warnings.add(invalid.code()); continue; }
            if (plan.warning() != null) {
                errors++; warnings.add(plan.warning());
                String value = row.path("status_code").asText("") + "/" + row.path("status").asText("");
                if (properties.apiKey() != null && !properties.apiKey().isBlank()) value = value.replace(properties.apiKey(), "[redacted]");
                value = value.replaceAll("[^a-zA-Z0-9 _+./:-]", "?");
                log.warn("SportStats match warning={} status={}", plan.warning(), value.substring(0, Math.min(value.length(), 60)));
            }
            if (existing.isPresent()) {
                var match = existing.get();
                if (!match.getSeason().getExternalId().equals(plan.season())
                        || !match.getSeason().getCompetition().getExternalId().equals(plan.league())) {
                    ignored++; errors++; warnings.add("MATCH_CONTEXT_CHANGED"); continue;
                }
                if (match.getSourceUpdatedAt() != null && plan.updatedAt() != null && plan.updatedAt().isBefore(match.getSourceUpdatedAt())) {
                    ignored++; warnings.add("STALE_PROVIDER_RECORD"); continue;
                }
            }
            var country = countries.findByIsoCode("RO").orElseGet(() -> countries.saveAndFlush(new Country("Romania", "RO")));
            var competition = competitions.findByProviderAndExternalId(PROVIDER, plan.league()).orElseGet(() -> {
                var item = new Competition(country, "SPORTSTATS-SUPERLIGA", "SuperLiga");
                item.identifyExternal(PROVIDER, plan.league());
                return competitions.saveAndFlush(item);
            });
            var season = seasons.findByProviderAndCompetitionIdAndExternalId(PROVIDER, competition.getId(), plan.season()).orElseGet(() -> {
                var item = new Season(competition, plan.season(), plan.start(), plan.end());
                item.identifyExternal(PROVIDER, plan.season());
                return seasons.saveAndFlush(item);
            });
            String phaseId = plan.division() + ":" + plan.stage().name();
            var phase = phases.findByProviderAndSeasonIdAndExternalId(PROVIDER, season.getId(), phaseId).orElseGet(() -> {
                var used = phases.findBySeasonIdOrderByPosition(season.getId()).stream().map(CompetitionPhase::getPosition).toList();
                int position = plan.stage().position;
                while (used.contains(position)) position++;
                var item = new CompetitionPhase(season, phaseId, plan.stage().label, PhaseFormat.LEAGUE, position, 3, 1, 0);
                item.identifyExternal(PROVIDER, phaseId);
                return phases.saveAndFlush(item);
            });
            var round = rounds.findByProviderAndPhaseIdAndExternalId(PROVIDER, phase.getId(), Integer.toString(plan.round())).orElseGet(() -> {
                var item = new Round(phase, plan.round(), "Etapa " + plan.round());
                item.identifyExternal(PROVIDER, Integer.toString(plan.round()));
                return rounds.saveAndFlush(item);
            });
            var home = team(country, plan.home());
            var away = team(country, plan.away());
            season.addTeam(home); season.addTeam(away);
            seasons.flush();
            var match = existing.orElseGet(() -> {
                var item = new Match(round, home, away, plan.kickoff(), plan.venue(), plan.status(), plan.homeScore(), plan.awayScore());
                item.identifyExternal(PROVIDER, plan.code());
                return item;
            });
            Instant sourceUpdatedAt = plan.updatedAt() == null ? match.getSourceUpdatedAt() : plan.updatedAt();
            boolean changed = !match.getRound().getId().equals(round.getId())
                    || !match.getHomeTeam().getId().equals(home.getId()) || !match.getAwayTeam().getId().equals(away.getId())
                    || !match.getKickoff().isEqual(plan.kickoff()) || !Objects.equals(match.getVenue(), plan.venue())
                    || match.getStatus() != plan.status() || !Objects.equals(match.getHomeScore(), plan.homeScore())
                    || !Objects.equals(match.getAwayScore(), plan.awayScore()) || !Objects.equals(match.getSourceUpdatedAt(), sourceUpdatedAt);
            match.updateImportedSchedule(round, home, away, plan.kickoff(), plan.venue(), sourceUpdatedAt);
            match.updateResult(plan.status(), plan.homeScore(), plan.awayScore());
            matches.saveAndFlush(match);
            if (existing.isEmpty()) created++; else if (changed) updated++; else ignored++;
        }
        return new SyncSummary(from, to, batch.pages(), created, updated, ignored, errors, List.copyOf(warnings), clock.instant());
    }

    private Team team(Country country, SportStatsMatchMapper.Side side) {
        var team = teams.findByProviderAndExternalId(PROVIDER, side.code()).orElseGet(() -> {
            var item = new Team(country, side.name(), side.shortName());
            item.identifyExternal(PROVIDER, side.code());
            return teams.saveAndFlush(item);
        });
        team.updateNames(side.name(), side.shortName());
        return team;
    }
}

package ro.superpont.football.sportstats.service;

import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ro.superpont.common.DomainException;
import ro.superpont.football.sportstats.client.*;
import ro.superpont.football.sportstats.dto.LogoSyncSummary;
import ro.superpont.football.sportstats.repository.SportStatsSyncRepository;
import ro.superpont.football.team.repository.TeamRepository;
import ro.superpont.football.team.service.TeamLogoCache;
import tools.jackson.databind.JsonNode;

/** Network work is outside transactions. Logo failures never participate in the match-import transaction. */
@Service
public class SportStatsLogoService {
    private final TeamRepository teams;
    private final SportStatsClient client;
    private final TeamLogoCache cache;
    private final SportStatsSyncRepository locks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public SportStatsLogoService(TeamRepository teams, SportStatsClient client, TeamLogoCache cache,
            SportStatsSyncRepository locks, TransactionTemplate transactions, Clock clock) {
        this.teams = teams; this.client = client; this.cache = cache; this.locks = locks;
        this.transactions = transactions; this.clock = clock;
    }
    private record Candidate(UUID id, String code, Long numericId, String hash, Instant retry) {}

    public LogoSyncSummary refresh(boolean force) {
        var all = transactions.execute(s -> teams.findByProviderOrderByExternalId("SPORTSTATS").stream()
                .map(t -> new Candidate(t.getId(), t.getExternalId(), t.getProviderTeamId(), t.getLogoHash(), t.getLogoRetryAfter())).toList());
        var candidates = all.stream().filter(t -> (force || !cache.contains(t.hash()))
                && (t.retry() == null || !t.retry().isAfter(clock.instant()))).limit(100).toList();
        int cached = (int) all.stream().filter(t -> cache.contains(t.hash())).count();
        if (candidates.isEmpty()) return new LogoSyncSummary(0, cached, all.size() - cached, 0, Set.of());
        var owner = UUID.randomUUID();
        if (!Boolean.TRUE.equals(transactions.execute(s -> locks.acquire(owner, clock.instant())))) {
            var retry = locks.retryAfter();
            if (retry != null && retry.isAfter(clock.instant())) throw new SportStatsException("RETRY_AFTER", retry);
            throw new DomainException(HttpStatus.CONFLICT, "O sincronizare SportStats este deja în desfășurare.");
        }
        int downloaded = 0, missing = 0, errors = 0;
        Instant backoff = null;
        var warnings = new TreeSet<String>();
        try {
            var leagues = transactions.execute(s -> teams.findProviderLeagues(candidates.stream().map(Candidate::id).toList()));
            var catalog = new HashMap<String, JsonNode>();
            for (String league : leagues) for (var row : client.fetchTeams(league)) {
                String code = row.path("code").asText(row.path("id").asText(""));
                if (!code.isBlank()) {
                    var previous = catalog.putIfAbsent(code, row);
                    if (previous != null && !previous.equals(row)) throw new SportStatsException("AMBIGUOUS_TEAM_IDENTITY");
                }
            }
            for (var team : candidates) {
                try {
                    heartbeat(owner);
                    var row = catalog.get(team.code());
                    if (row == null) throw new SportStatsException("TEAM_NOT_IN_CATALOG");
                    var source = client.logoSource(row);
                    if (source == null) throw new SportStatsException("LOGO_NOT_FOUND");
                    if (team.numericId() != null && !team.numericId().equals(source.numericId()))
                        throw new SportStatsException("TEAM_IDENTITY_CHANGED");
                    var image = client.downloadLogo(source);
                    String hash = cache.store(image.bytes(), image.contentType());
                    transactions.executeWithoutResult(s -> {
                        if (!locks.guard(owner, clock.instant())) throw new SportStatsException("SYNC_LEASE_EXPIRED");
                        var entity = teams.findById(team.id()).orElseThrow();
                        entity.cacheLogo(hash, source.uri().toASCIIString(), source.credit(), source.numericId(), clock.instant());
                    });
                    downloaded++;
                } catch (SportStatsException e) {
                    warnings.add(e.code());
                    if (Set.of("API_RATE_LIMITED", "API_AUTHENTICATION", "PAYMENT_REQUIRED", "SYNC_LEASE_EXPIRED", "API_KEY_MISSING").contains(e.code())
                            || e.retryAfter() != null) throw e;
                    boolean absent = Set.of("LOGO_NOT_FOUND", "TEAM_NOT_IN_CATALOG").contains(e.code());
                    if (absent) missing++; else errors++;
                    transactions.executeWithoutResult(s -> {
                        if (!locks.guard(owner, clock.instant())) throw new SportStatsException("SYNC_LEASE_EXPIRED");
                        teams.findById(team.id()).ifPresent(t -> t.deferLogo(clock.instant(), clock.instant().plusSeconds(absent ? 86400 : 3600)));
                    });
                }
            }
            int retained = Math.max(0, cached - (int) candidates.stream().filter(t -> cache.contains(t.hash())).count());
            var result = new LogoSyncSummary(downloaded, retained, missing, errors, Collections.unmodifiableSet(warnings));
            LoggerFactory.getLogger(getClass()).info("SportStats logos downloaded={} cached={} missing={} errors={} warnings={}", downloaded, cached, missing, errors, warnings);
            return result;
        } catch (SportStatsException e) {
            backoff = e.retryAfter() != null ? e.retryAfter() : clock.instant().plusSeconds(
                    Set.of("API_AUTHENTICATION", "PAYMENT_REQUIRED", "API_KEY_MISSING").contains(e.code()) ? 3600 : 60);
            LoggerFactory.getLogger(getClass()).warn("SportStats logos downloaded={} errors={} code={}", downloaded, errors + 1, e.code());
            throw e;
        } finally {
            Instant retry = backoff;
            transactions.executeWithoutResult(s -> locks.release(owner, retry));
        }
    }

    private void heartbeat(UUID owner) {
        transactions.executeWithoutResult(s -> {
            if (!locks.renew(owner, clock.instant())) throw new SportStatsException("SYNC_LEASE_EXPIRED");
        });
    }
}

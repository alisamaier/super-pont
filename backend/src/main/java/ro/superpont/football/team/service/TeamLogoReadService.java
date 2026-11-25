package ro.superpont.football.team.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.common.DomainException;
import ro.superpont.football.team.repository.TeamRepository;

@Service
@Transactional(readOnly = true)
public class TeamLogoReadService {
    private final TeamRepository teams;
    private final TeamLogoCache cache;
    public TeamLogoReadService(TeamRepository teams, TeamLogoCache cache) { this.teams = teams; this.cache = cache; }
    public record CachedLogo(byte[] png, String hash) {}
    public CachedLogo get(UUID id) {
        var team = teams.findById(id).orElseThrow(() -> DomainException.missing("Team"));
        if (!cache.contains(team.getLogoHash())) throw DomainException.missing("Team logo");
        return new CachedLogo(cache.read(team.getLogoHash()), team.getLogoHash());
    }
}

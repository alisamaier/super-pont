package ro.superpont.football.team.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.superpont.football.team.model.TeamLogoImage;
import ro.superpont.football.team.repository.TeamLogoImageRepository;
import ro.superpont.football.sportstats.client.SportStatsException;

@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "app.team-logos.storage", havingValue = "database")
public class DatabaseTeamLogoStore implements TeamLogoStore {
    private final TeamLogoImageRepository images;
    public DatabaseTeamLogoStore(TeamLogoImageRepository images) { this.images = images; }
    public boolean contains(String hash) { return hash != null && images.existsById(hash); }
    public byte[] read(String hash) {
        return images.findById(hash).orElseThrow(() -> new SportStatsException("LOGO_CACHE_FAILED")).getPng();
    }
    @Transactional
    public void write(String hash, byte[] png) {
        if (!images.existsById(hash)) images.save(new TeamLogoImage(hash, png));
    }
}

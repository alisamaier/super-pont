package ro.superpont.football.sportstats.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import ro.superpont.football.sportstats.client.SportStatsException;
import ro.superpont.common.DomainException;

@Component
@ConditionalOnProperty(name = "app.team-logos.automatic-enabled", havingValue = "true")
public class SportStatsLogoScheduler {
    private final SportStatsLogoService logos;
    private final SportStatsProperties properties;
    public SportStatsLogoScheduler(SportStatsLogoService logos, SportStatsProperties properties) { this.logos = logos; this.properties = properties; }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${app.team-logos.check-interval:PT5M}")
    public void missingLogos() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) return;
        try { logos.refresh(false); }
        catch (SportStatsException | DomainException e) { /* Shared lease/backoff protects both imports. */ }
        catch (RuntimeException e) { LoggerFactory.getLogger(getClass()).warn("SportStats logo check failed; cached images preserved."); }
    }
}

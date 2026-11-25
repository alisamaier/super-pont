package ro.superpont.football.sportstats.service;

import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.superpont.football.sportstats.model.SportStatsProperties;

@Component
@ConditionalOnProperty(prefix = "app.sportstats", name = "sync-enabled", havingValue = "true")
public class SportStatsScheduler {
    private final SportStatsSyncService sync;
    private final SportStatsProperties properties;
    private final Clock clock;
    public SportStatsScheduler(SportStatsSyncService sync, SportStatsProperties properties, Clock clock) {
        this.sync = sync; this.properties = properties; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${app.sportstats.sync-interval}", initialDelayString = "${app.sportstats.sync-interval}")
    public void synchronize() {
        var today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        try { sync.sync(today.minusDays(properties.daysBack()), today.plusDays(properties.daysAhead())); }
        catch (RuntimeException ignored) {
            // Details are deliberately excluded: provider errors may contain credentials or untrusted content.
            LoggerFactory.getLogger(SportStatsScheduler.class).warn("SportStats automatic synchronization did not complete; saved data retained");
        }
    }
}

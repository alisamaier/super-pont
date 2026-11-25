package ro.superpont.football.sportstats.model;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.sportstats")
public record SportStatsProperties(
        String apiKey, boolean syncEnabled, @NotNull Duration syncInterval,
        @NotNull Duration connectTimeout, @NotNull Duration requestTimeout,
        @Min(1) @Max(50) int maxPages, @Min(0) @Max(365) int daysBack, @Min(0) @Max(365) int daysAhead,
        @Min(1) @Max(12) int seasonStartMonth, @Min(1) @Max(28) int seasonStartDay,
        @NotBlank String phaseDivisions) {
    @AssertTrue(message = "SportStats intervals and timeouts must be positive and bounded")
    public boolean isTimingValid() {
        return syncInterval != null && syncInterval.compareTo(Duration.ofMinutes(1)) >= 0
                && connectTimeout != null && connectTimeout.compareTo(Duration.ofSeconds(1)) >= 0
                && connectTimeout.compareTo(Duration.ofSeconds(10)) <= 0
                && requestTimeout != null && requestTimeout.compareTo(Duration.ofSeconds(1)) >= 0
                && requestTimeout.compareTo(Duration.ofSeconds(15)) <= 0;
    }
    @Override public String toString() { return "SportStatsProperties[syncEnabled=" + syncEnabled + "]"; }
}

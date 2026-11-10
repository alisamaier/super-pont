package ro.superpont.auth.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.rate-limit")
public record AuthRateLimitProperties(
        @NotNull @Valid Limit loginIp,
        @NotNull @Valid Limit registrationIp,
        @NotNull @Valid Limit loginUsername) {

    public record Limit(
            @Positive int attempts,
            @NotNull @DurationMin(seconds = 1) Duration window) {}
}

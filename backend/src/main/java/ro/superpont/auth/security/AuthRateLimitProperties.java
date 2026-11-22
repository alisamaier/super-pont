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
        @NotNull @Valid Limit loginEmail,
        @NotNull @Valid Limit verificationIp,
        @NotNull @Valid Limit verificationRegistration,
        @NotNull @Valid Limit emailIp,
        @NotNull @Valid Limit emailRegistration,
        @NotNull @Valid Limit passwordResetIp,
        @NotNull @Valid Limit passwordResetEmail,
        @NotNull @Valid Limit passwordResetSubmissionIp,
        @NotNull @Valid Limit passwordResetToken) {

    public record Limit(
            @Positive int attempts,
            @NotNull @DurationMin(seconds = 1) Duration window) {}
}

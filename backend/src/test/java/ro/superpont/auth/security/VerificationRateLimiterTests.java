package ro.superpont.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class VerificationRateLimiterTests {
    @Test
    void passwordRecoveryBudgetsNormalizeEmailsAndRemainSeparateFromLoginAndVerification() {
        var limit = new AuthRateLimitProperties.Limit(2, Duration.ofHours(1));
        var limiter = new AuthRateLimiter(new AuthRateLimitProperties(limit, limit, limit, limit, limit,
                limit, limit, limit, limit, limit, limit));
        limiter.checkPasswordResetEmail(" Alisa@Example.test ");
        limiter.checkPasswordResetEmail("alisa@example.test");
        assertThatThrownBy(() -> limiter.checkPasswordResetEmail("ALISA@EXAMPLE.TEST")).isInstanceOf(AuthRateLimitException.class);
        limiter.checkPasswordResetEmail("another@example.test");
        limiter.checkPasswordResetIp("ip"); limiter.checkPasswordResetIp("ip");
        assertThatThrownBy(() -> limiter.checkPasswordResetIp("ip")).isInstanceOf(AuthRateLimitException.class);
        limiter.checkPasswordResetSubmissionIp("ip"); limiter.checkPasswordResetSubmissionIp("ip");
        assertThatThrownBy(() -> limiter.checkPasswordResetSubmissionIp("ip")).isInstanceOf(AuthRateLimitException.class);
        limiter.checkPasswordResetToken("hash"); limiter.checkPasswordResetToken("hash");
        assertThatThrownBy(() -> limiter.checkPasswordResetToken("hash")).isInstanceOf(AuthRateLimitException.class);
        limiter.checkLoginEmail("alisa@example.test");
        limiter.checkVerificationIp("ip");
    }

    @Test
    void emailLimitsApplyAcrossRegistrationsAndIpsAndVerificationHasItsOwnBudget() {
        var minute = new AuthRateLimitProperties.Limit(2, Duration.ofMinutes(1));
        var hour = new AuthRateLimitProperties.Limit(2, Duration.ofHours(1));
        var limiter = new AuthRateLimiter(new AuthRateLimitProperties(minute, minute, minute,
                minute, minute, hour, hour, hour, hour, minute, minute));
        limiter.checkEmailIp("ip");
        limiter.checkEmailIp("ip");
        assertThatThrownBy(() -> limiter.checkEmailIp("ip")).isInstanceOf(AuthRateLimitException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((AuthRateLimitException) exception).getRetryAfter()).isBetween(3590L, 3600L));
        limiter.checkEmailRegistration("same-account-after-token-rotation");
        limiter.checkEmailRegistration("same-account-after-token-rotation");
        assertThatThrownBy(() -> limiter.checkEmailRegistration("same-account-after-token-rotation"))
                .isInstanceOf(AuthRateLimitException.class);
        limiter.checkVerificationIp("ip");
        limiter.checkVerificationIp("ip");
        assertThatThrownBy(() -> limiter.checkVerificationIp("ip")).isInstanceOf(AuthRateLimitException.class);
        limiter.checkVerificationRegistration("account");
        limiter.checkVerificationRegistration("account");
        assertThatThrownBy(() -> limiter.checkVerificationRegistration("account")).isInstanceOf(AuthRateLimitException.class);
    }
}

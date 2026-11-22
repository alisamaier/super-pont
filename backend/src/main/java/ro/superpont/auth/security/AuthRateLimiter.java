package ro.superpont.auth.security;

import ro.superpont.auth.validation.EmailAddress;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public final class AuthRateLimiter {
    private static final int MAX_KEYS_PER_LIMIT = 10_000;

    private final SlidingWindowLimiter loginIp;
    private final SlidingWindowLimiter registrationIp;
    private final SlidingWindowLimiter loginEmail;
    private final SlidingWindowLimiter verificationIp;
    private final SlidingWindowLimiter verificationRegistration;
    private final SlidingWindowLimiter emailIp;
    private final SlidingWindowLimiter emailRegistration;
    private final SlidingWindowLimiter passwordResetIp;
    private final SlidingWindowLimiter passwordResetEmail;
    private final SlidingWindowLimiter passwordResetSubmissionIp;
    private final SlidingWindowLimiter passwordResetToken;

    public AuthRateLimiter(AuthRateLimitProperties properties) {
        loginIp = limiter(properties.loginIp());
        registrationIp = limiter(properties.registrationIp());
        loginEmail = limiter(properties.loginEmail());
        verificationIp = limiter(properties.verificationIp());
        verificationRegistration = limiter(properties.verificationRegistration());
        emailIp = limiter(properties.emailIp());
        emailRegistration = limiter(properties.emailRegistration());
        passwordResetIp = limiter(properties.passwordResetIp());
        passwordResetEmail = limiter(properties.passwordResetEmail());
        passwordResetSubmissionIp = limiter(properties.passwordResetSubmissionIp());
        passwordResetToken = limiter(properties.passwordResetToken());
    }

    private static SlidingWindowLimiter limiter(AuthRateLimitProperties.Limit limit) {
        return new SlidingWindowLimiter(limit.attempts(), limit.window(), MAX_KEYS_PER_LIMIT, System::nanoTime);
    }

    public void checkLoginIp(String address) {
        check(loginIp.acquire(address));
    }

    public void checkRegistrationIp(String address) {
        check(registrationIp.acquire(address));
    }

    public void checkLoginEmail(String email) {
        check(loginEmail.acquire(EmailAddress.normalize(email)));
    }

    public void checkVerificationIp(String address) { check(verificationIp.acquire(address)); }
    public void checkVerificationRegistration(String id) { check(verificationRegistration.acquire(id)); }
    public void checkEmailIp(String address) { check(emailIp.acquire(address)); }
    public void checkEmailRegistration(String id) { check(emailRegistration.acquire(id)); }
    public void checkPasswordResetIp(String address) { check(passwordResetIp.acquire(address)); }
    public void checkPasswordResetEmail(String email) { check(passwordResetEmail.acquire(EmailAddress.normalize(email))); }
    public void checkPasswordResetSubmissionIp(String address) { check(passwordResetSubmissionIp.acquire(address)); }
    public void checkPasswordResetToken(String hash) { check(passwordResetToken.acquire(hash)); }

    private static void check(long retryAfter) {
        if (retryAfter > 0) {
            throw new AuthRateLimitException(retryAfter);
        }
    }
}

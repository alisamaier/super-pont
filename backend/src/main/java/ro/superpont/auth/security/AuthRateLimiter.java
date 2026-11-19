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

    public AuthRateLimiter(AuthRateLimitProperties properties) {
        loginIp = limiter(properties.loginIp());
        registrationIp = limiter(properties.registrationIp());
        loginEmail = limiter(properties.loginEmail());
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

    private static void check(long retryAfter) {
        if (retryAfter > 0) {
            throw new AuthRateLimitException(retryAfter);
        }
    }
}

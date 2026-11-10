package ro.superpont.auth.security;

public final class AuthRateLimitException extends RuntimeException {
    private final long retryAfter;

    AuthRateLimitException(long retryAfter) {
        super("Too many authentication requests");
        this.retryAfter = retryAfter;
    }

    public long getRetryAfter() {
        return retryAfter;
    }
}

package ro.superpont.football.sportstats.client;

import java.time.Instant;

/** Only controlled error codes cross this boundary; never retain a provider response or HTTP exception. */
public class SportStatsException extends RuntimeException {
    private final String code;
    private final Instant retryAfter;
    public SportStatsException(String code) { this(code, null); }
    public SportStatsException(String code, Instant retryAfter) {
        super(code);
        this.code = code;
        this.retryAfter = retryAfter;
    }
    public String code() { return code; }
    public Instant retryAfter() { return retryAfter; }
}

package ro.superpont.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SlidingWindowLimiterTests {
    @Test
    void expiresAttemptsIndividuallyAndRoundsRetryAfterUp() {
        var clock = new AtomicLong();
        var limiter = new SlidingWindowLimiter(2, Duration.ofMinutes(1), 10, clock::get);

        assertThat(limiter.acquire("key")).isZero();
        clock.set(Duration.ofSeconds(10).toNanos());
        assertThat(limiter.acquire("key")).isZero();
        clock.set(Duration.ofMillis(59_500).toNanos());
        assertThat(limiter.acquire("key")).isEqualTo(1);
        clock.set(Duration.ofSeconds(60).toNanos());
        assertThat(limiter.acquire("key")).isZero();
        assertThat(limiter.acquire("key")).isEqualTo(10);
    }

    @Test
    void boundsTheNumberOfKeysWithoutEvictingActiveLimits() {
        var clock = new AtomicLong();
        var limiter = new SlidingWindowLimiter(1, Duration.ofMinutes(1), 1, clock::get);

        assertThat(limiter.acquire("first")).isZero();
        assertThat(limiter.acquire("second")).isEqualTo(60);
        assertThat(limiter.acquire("first")).isEqualTo(60);
        clock.set(Duration.ofSeconds(60).toNanos());
        assertThat(limiter.acquire("second")).isZero();
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        var limiter = new SlidingWindowLimiter(5, Duration.ofMinutes(1), 10, () -> 0L);
        var requests = IntStream.range(0, 30)
                .mapToObj(i -> (Callable<Long>) () -> limiter.acquire("same"))
                .toList();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(requests);
            int admitted = 0;
            for (var result : results) {
                if (result.get() == 0) {
                    admitted++;
                }
            }
            assertThat(admitted).isEqualTo(5);
        }
    }
}

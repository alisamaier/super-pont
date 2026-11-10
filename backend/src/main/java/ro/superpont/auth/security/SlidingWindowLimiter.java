package ro.superpont.auth.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

final class SlidingWindowLimiter {
    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    private final int limit;
    private final long windowNanos;
    private final int maxKeys;
    private final LongSupplier clock;
    private final LinkedHashMap<String, ArrayDeque<Long>> requests = new LinkedHashMap<>();

    SlidingWindowLimiter(int limit, Duration window, int maxKeys, LongSupplier clock) {
        this.limit = limit;
        this.windowNanos = window.toNanos();
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    // Zero admits the request. Otherwise the result is Retry-After in whole seconds.
    synchronized long acquire(String key) {
        long now = clock.getAsLong();
        var entries = requests.values().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getLast() < windowNanos) {
                break;
            }
            entries.remove();
        }

        var timestamps = requests.get(key);
        if (timestamps == null) {
            if (requests.size() == maxKeys) {
                return retryAfter(requests.firstEntry().getValue().getLast(), now);
            }
            timestamps = new ArrayDeque<>();
        } else {
            while (!timestamps.isEmpty() && now - timestamps.getFirst() >= windowNanos) {
                timestamps.removeFirst();
            }
            if (timestamps.size() == limit) {
                return retryAfter(timestamps.getFirst(), now);
            }
            requests.remove(key);
        }

        timestamps.addLast(now);
        requests.put(key, timestamps);
        return 0;
    }

    private long retryAfter(long timestamp, long now) {
        return Math.ceilDiv(windowNanos - (now - timestamp), NANOS_PER_SECOND);
    }
}

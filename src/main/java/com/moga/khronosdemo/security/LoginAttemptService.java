package com.moga.khronosdemo.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Counts failed sign-ins in memory, per username and client IP and per client IP alone. Once a pair
 * reaches maxAttempts failures inside the window it is blocked until that window ends, and a successful
 * sign-in clears it. Once an IP reaches maxAttemptsPerIp failures inside the window, whatever the
 * usernames, every sign-in from it is blocked until that window ends, so one client cannot spread its
 * guesses over many usernames or flood the table with made-up ones. A successful sign-in does not clear
 * the IP's count, or one valid account would let a client wipe out the failures it made against others.
 * <p>
 * Each table keeps at most MAX_TRACKED entries, so memory stays bounded. When a table is full, expired
 * entries are dropped first, then the least recently seen entry that is not blocked, and only when every
 * entry is blocked the least recently seen blocked one. Failures that stay under the limit can therefore
 * never push a blocked pair or IP out of the table.
 * The counts are per instance and are lost on restart.
 */
@Component
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptService {

    static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final FailureCounts pairs;
    private final FailureCounts ips;

    @Autowired
    public LoginAttemptService(LoginAttemptProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptService(LoginAttemptProperties properties, Clock clock) {
        if (properties.maxAttempts() < 1 || properties.maxAttemptsPerIp() < 1 || properties.window() == null
                || properties.window().isNegative() || properties.window().isZero()) {
            throw new IllegalStateException("app.login.max-attempts and app.login.max-attempts-per-ip must be at least 1"
                    + " and app.login.window must be positive");
        }
        this.clock = clock;
        this.pairs = new FailureCounts(properties.maxAttempts(), properties.window());
        this.ips = new FailureCounts(properties.maxAttemptsPerIp(), properties.window());
    }

    /**
     * @return how many seconds the pair must wait before trying again, because the pair or its IP is
     * blocked, or 0 if it may try now
     */
    public synchronized long secondsUntilAllowed(String username, String clientIp) {
        Instant now = clock.instant();
        Duration pairWait = pairs.timeUntilAllowed(key(username, clientIp), now);
        Duration ipWait = ips.timeUntilAllowed(clientIp, now);
        Duration wait = pairWait.compareTo(ipWait) >= 0 ? pairWait : ipWait;
        if (wait.isZero()) {
            return 0;
        }
        long millis = wait.toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    public synchronized void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        pairs.recordFailure(key(username, clientIp), now);
        ips.recordFailure(clientIp, now);
    }

    public synchronized void recordSuccess(String username, String clientIp) {
        pairs.clear(key(username, clientIp));
    }

    synchronized int trackedPairs() {
        return pairs.size();
    }

    synchronized int trackedIps() {
        return ips.size();
    }

    private static String key(String username, String clientIp) {
        return username + '\n' + clientIp;
    }

    /** Failures per key inside a fixed window that starts with the first failure. Not thread-safe on its own. */
    private static final class FailureCounts {

        private final int maxFailures;
        private final Duration window;
        // Access order, so iteration starts with the least recently seen entry
        private final LinkedHashMap<String, Attempts> entries = new LinkedHashMap<>(16, 0.75f, true);

        FailureCounts(int maxFailures, Duration window) {
            this.maxFailures = maxFailures;
            this.window = window;
        }

        Duration timeUntilAllowed(String key, Instant now) {
            Attempts entry = entries.get(key);
            if (entry == null || entry.failures() < maxFailures || isExpired(entry, now)) {
                return Duration.ZERO;
            }
            return Duration.between(now, entry.windowStart().plus(window));
        }

        void recordFailure(String key, Instant now) {
            Attempts entry = entries.get(key);
            if (entry == null || isExpired(entry, now)) {
                if (entry == null) {
                    makeRoom(now);
                }
                entries.put(key, new Attempts(1, now));
            } else {
                entries.put(key, new Attempts(entry.failures() + 1, entry.windowStart()));
            }
        }

        void clear(String key) {
            entries.remove(key);
        }

        int size() {
            return entries.size();
        }

        private void makeRoom(Instant now) {
            if (entries.size() < MAX_TRACKED) {
                return;
            }
            entries.values().removeIf(entry -> isExpired(entry, now));
            if (entries.size() < MAX_TRACKED) {
                return;
            }
            // Everything left is inside its window: drop the least recently seen entry that is not blocked
            Iterator<Attempts> candidates = entries.values().iterator();
            while (candidates.hasNext()) {
                if (candidates.next().failures() < maxFailures) {
                    candidates.remove();
                    return;
                }
            }
            // Every entry is blocked, so the least recently seen one has to go to keep memory bounded
            entries.remove(entries.keySet().iterator().next());
        }

        private boolean isExpired(Attempts entry, Instant now) {
            return !now.isBefore(entry.windowStart().plus(window));
        }
    }

    private record Attempts(int failures, Instant windowStart) {
    }
}

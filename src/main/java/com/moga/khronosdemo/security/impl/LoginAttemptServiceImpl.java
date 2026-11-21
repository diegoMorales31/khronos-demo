package com.moga.khronosdemo.security.impl;

import com.moga.khronosdemo.security.LoginAttemptProperties;
import com.moga.khronosdemo.security.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Counts failed sign-ins in memory, per username and client IP, per client IP alone and per username
 * alone. Once a pair reaches maxAttempts failures inside the window it is blocked until that window ends,
 * and a successful sign-in clears it. Once an IP reaches maxAttemptsPerIp failures inside the window,
 * whatever the usernames, every sign-in from it is blocked until that window ends, so one client cannot
 * spread its guesses over many usernames or flood the table with made-up ones. A successful sign-in does
 * not clear the IP's count, or one valid account would let a client wipe out the failures it made against
 * others.
 * <p>
 * Once a username reaches maxAttemptsPerUser failures inside userWindow, whatever the IPs, it is blocked
 * until that window ends, so guesses spread over many addresses still run out. That block would also let
 * anyone lock the real user out on purpose, so it does not apply to an IP the username signed in from
 * successfully in the last KNOWN_FOR: the user's own devices keep working. A success does not clear the
 * username's count either, for the same reason as the IP's.
 * <p>
 * Each table keeps at most MAX_TRACKED entries, so memory stays bounded. When a table is full, expired
 * entries are dropped first, then the least recently seen entry that is not blocked, and only when every
 * entry is blocked the least recently seen blocked one. Failures that stay under the limit can therefore
 * never push a blocked pair, IP or username out of the table.
 * The counts are per instance and are lost on restart.
 */
@Service
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptServiceImpl implements LoginAttemptService {

    static final int MAX_TRACKED = 10_000;
    static final Duration KNOWN_FOR = Duration.ofDays(30);

    private final Clock clock;
    private final FailureCounts pairs;
    private final FailureCounts ips;
    private final FailureCounts users;
    private final KnownPairs knownPairs = new KnownPairs();

    @Autowired
    public LoginAttemptServiceImpl(LoginAttemptProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptServiceImpl(LoginAttemptProperties properties, Clock clock) {
        if (properties.maxAttempts() < 1 || properties.maxAttemptsPerIp() < 1 || properties.maxAttemptsPerUser() < 1
                || !isPositive(properties.window()) || !isPositive(properties.userWindow())) {
            throw new IllegalStateException("app.login.max-attempts, app.login.max-attempts-per-ip and"
                    + " app.login.max-attempts-per-user must be at least 1, and app.login.window and"
                    + " app.login.user-window must be positive");
        }
        this.clock = clock;
        this.pairs = new FailureCounts(properties.maxAttempts(), properties.window());
        this.ips = new FailureCounts(properties.maxAttemptsPerIp(), properties.window());
        this.users = new FailureCounts(properties.maxAttemptsPerUser(), properties.userWindow());
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    /**
     * @return how many seconds the pair must wait before trying again, because the pair, its IP or its
     * username is blocked, or 0 if it may try now. The username's block is skipped for a known pair.
     */
    @Override
    public synchronized long secondsUntilAllowed(String username, String clientIp) {
        Instant now = clock.instant();
        String pair = key(username, clientIp);
        Duration wait = longest(pairs.timeUntilAllowed(pair, now), ips.timeUntilAllowed(clientIp, now));
        if (!knownPairs.contains(pair, now)) {
            wait = longest(wait, users.timeUntilAllowed(username, now));
        }
        if (wait.isZero()) {
            return 0;
        }
        long millis = wait.toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    @Override
    public synchronized void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        pairs.recordFailure(key(username, clientIp), now);
        ips.recordFailure(clientIp, now);
        users.recordFailure(username, now);
    }

    @Override
    public synchronized void recordSuccess(String username, String clientIp) {
        String pair = key(username, clientIp);
        pairs.clear(pair);
        knownPairs.add(pair, clock.instant());
    }

    synchronized int trackedPairs() {
        return pairs.size();
    }

    synchronized int trackedIps() {
        return ips.size();
    }

    synchronized int trackedUsers() {
        return users.size();
    }

    synchronized int trackedKnownPairs() {
        return knownPairs.size();
    }

    private static String key(String username, String clientIp) {
        return username + '\n' + clientIp;
    }

    private static Duration longest(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Pairs that signed in successfully, each kept for KNOWN_FOR after its last success. Not thread-safe on its own. */
    private static final class KnownPairs {

        // Access order, so iteration starts with the least recently seen pair
        private final LinkedHashMap<String, Instant> lastSuccess = new LinkedHashMap<>(16, 0.75f, true);

        boolean contains(String pair, Instant now) {
            Instant last = lastSuccess.get(pair);
            return last != null && now.isBefore(last.plus(KNOWN_FOR));
        }

        void add(String pair, Instant now) {
            if (!lastSuccess.containsKey(pair) && lastSuccess.size() >= MAX_TRACKED) {
                lastSuccess.values().removeIf(last -> !now.isBefore(last.plus(KNOWN_FOR)));
                if (lastSuccess.size() >= MAX_TRACKED) {
                    lastSuccess.remove(lastSuccess.keySet().iterator().next());
                }
            }
            lastSuccess.put(pair, now);
        }

        int size() {
            return lastSuccess.size();
        }
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

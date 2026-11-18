package com.moga.khronosdemo.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTests {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service =
            new LoginAttemptService(new LoginAttemptProperties(3, Duration.ofMinutes(15), 10), clock);

    @Test
    void blocksThePairAfterTheMaximumFailuresUntilTheWindowEnds() {
        fail(3, "ana", "10.0.0.1");
        assertEquals(15 * 60, service.secondsUntilAllowed("ana", "10.0.0.1"));

        clock.advance(Duration.ofMinutes(10).plusMillis(1));
        assertEquals(5 * 60, service.secondsUntilAllowed("ana", "10.0.0.1"));

        clock.advance(Duration.ofMinutes(5));
        assertEquals(0, service.secondsUntilAllowed("ana", "10.0.0.1"));
    }

    @Test
    void failuresBelowTheMaximumDoNotBlock() {
        fail(2, "ana", "10.0.0.1");
        assertEquals(0, service.secondsUntilAllowed("ana", "10.0.0.1"));
    }

    @Test
    void aFreshWindowStartsOnceTheOldOneHasExpired() {
        fail(2, "ana", "10.0.0.1");
        clock.advance(Duration.ofMinutes(16));

        fail(2, "ana", "10.0.0.1");
        assertEquals(0, service.secondsUntilAllowed("ana", "10.0.0.1"));
    }

    @Test
    void usernameAndIpAreCountedTogether() {
        fail(3, "ana", "10.0.0.1");

        assertEquals(0, service.secondsUntilAllowed("ana", "10.0.0.2"));
        assertEquals(0, service.secondsUntilAllowed("bob", "10.0.0.1"));
    }

    @Test
    void successClearsTheFailures() {
        fail(2, "ana", "10.0.0.1");
        service.recordSuccess("ana", "10.0.0.1");
        fail(2, "ana", "10.0.0.1");

        assertEquals(0, service.secondsUntilAllowed("ana", "10.0.0.1"));
    }

    @Test
    void blocksAnIpAfterTheMaximumFailuresWhateverTheUsernames() {
        for (int i = 0; i < 10; i++) {
            service.recordFailure("user" + i, "10.0.0.1");
        }

        assertEquals(15 * 60, service.secondsUntilAllowed("someone-else", "10.0.0.1"));
        assertEquals(0, service.secondsUntilAllowed("someone-else", "10.0.0.2"));

        clock.advance(Duration.ofMinutes(15));
        assertEquals(0, service.secondsUntilAllowed("someone-else", "10.0.0.1"));
    }

    @Test
    void successDoesNotClearTheIpsFailures() {
        for (int i = 0; i < 9; i++) {
            service.recordFailure("user" + i, "10.0.0.1");
        }
        service.recordSuccess("ana", "10.0.0.1");
        service.recordFailure("user9", "10.0.0.1");

        assertEquals(15 * 60, service.secondsUntilAllowed("ana", "10.0.0.1"));
    }

    @Test
    void madeUpUsernamesFromTheSameIpCannotPushOutABlockedPair() {
        fail(3, "ana", "10.0.0.1");

        // The attack: fill the table with made-up usernames from the same address, going through
        // the same check as the login endpoint, so that ana's blocked pair is dropped
        int accepted = 0;
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED + 500; i++) {
            if (service.secondsUntilAllowed("made-up" + i, "10.0.0.1") == 0) {
                service.recordFailure("made-up" + i, "10.0.0.1");
                accepted++;
            }
        }

        // The address is blocked once it reaches its own limit, long before the table is full
        assertEquals(10 - 3, accepted);
        assertEquals(15 * 60, service.secondsUntilAllowed("ana", "10.0.0.1"));
        assertEquals(15 * 60, service.secondsUntilAllowed("made-up-next", "10.0.0.1"));
    }

    @Test
    void failuresUnderTheLimitNeverPushOutABlockedPairOrIp() {
        fail(3, "ana", "10.0.0.1");
        for (int i = 0; i < 10; i++) {
            service.recordFailure("user" + i, "10.0.0.2");
        }

        // A flood of pairs and addresses with one failure each, none of them blocked
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED + 500; i++) {
            service.recordFailure("made-up" + i, ip(i));
        }

        assertEquals(LoginAttemptService.MAX_TRACKED, service.trackedPairs());
        assertEquals(LoginAttemptService.MAX_TRACKED, service.trackedIps());
        assertEquals(15 * 60, service.secondsUntilAllowed("ana", "10.0.0.1"));
        assertEquals(15 * 60, service.secondsUntilAllowed("someone-else", "10.0.0.2"));
    }

    @Test
    void keepsAtMostTheMaximumNumberOfPairs() {
        for (int i = 0; i < LoginAttemptService.MAX_TRACKED + 500; i++) {
            service.recordFailure("user" + i, ip(i));
        }
        assertEquals(LoginAttemptService.MAX_TRACKED, service.trackedPairs());
        assertEquals(LoginAttemptService.MAX_TRACKED, service.trackedIps());

        // Once their window is over, old pairs make room before any recent one is dropped
        clock.advance(Duration.ofMinutes(16));
        service.recordFailure("newcomer", "10.0.0.1");
        assertEquals(1, service.trackedPairs());
        assertEquals(1, service.trackedIps());
    }

    @Test
    void staysBoundedWhenEveryPairIsBlocked() {
        LoginAttemptService strict =
                new LoginAttemptService(new LoginAttemptProperties(1, Duration.ofMinutes(15), 1), clock);

        for (int i = 0; i < LoginAttemptService.MAX_TRACKED + 500; i++) {
            strict.recordFailure("user" + i, ip(i));
        }

        // With nothing else to drop, the least recently seen blocked entries give way
        assertEquals(LoginAttemptService.MAX_TRACKED, strict.trackedPairs());
        assertEquals(LoginAttemptService.MAX_TRACKED, strict.trackedIps());
        assertEquals(0, strict.secondsUntilAllowed("user0", ip(0)));
        int last = LoginAttemptService.MAX_TRACKED + 499;
        assertEquals(15 * 60, strict.secondsUntilAllowed("user" + last, ip(last)));
    }

    @Test
    void rejectsSettingsThatWouldNeverBlockOrNeverUnblock() {
        assertThrows(IllegalStateException.class,
                () -> new LoginAttemptService(new LoginAttemptProperties(0, Duration.ofMinutes(15), 50)));
        assertThrows(IllegalStateException.class,
                () -> new LoginAttemptService(new LoginAttemptProperties(5, Duration.ofMinutes(15), 0)));
        assertThrows(IllegalStateException.class,
                () -> new LoginAttemptService(new LoginAttemptProperties(5, Duration.ZERO, 50)));
    }

    private void fail(int times, String username, String ip) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username, ip);
        }
    }

    // A distinct address in 10.1.0.0/16 for each number below 65536, never one of the 10.0.0.x used above
    private static String ip(int n) {
        return "10.1." + (n / 256) + "." + (n % 256);
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2030-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

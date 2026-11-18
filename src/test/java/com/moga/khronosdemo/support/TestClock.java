package com.moga.khronosdemo.support;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test-only clock that replaces the application's Clock bean. It follows the real time until a test
 * fixes it with {@link #setNow(Instant)}; IntegrationTest goes back to the real time around each test.
 */
@Component
@Primary
public class TestClock extends Clock {

    private volatile Instant fixed;

    public void setNow(Instant now) {
        this.fixed = now;
    }

    public void advance(Duration duration) {
        this.fixed = instant().plus(duration);
    }

    public void useRealTime() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant now = fixed;
        return now != null ? now : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }
}

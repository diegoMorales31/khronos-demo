package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param lateThreshold    a shift whose first clock-in is more than this after it starts counts as late
 * @param shiftMatchWindow a clock-in up to this long before a shift starts (or while it runs) is matched to it
 * @param staleAfter       an entry still open after this long is flagged as stale, a likely forgotten clock-out
 */
@ConfigurationProperties(prefix = "app.time-tracking")
public record TimeTrackingProperties(
        Duration lateThreshold,
        Duration shiftMatchWindow,
        Duration staleAfter
) {

    public TimeTrackingProperties {
        if (isNegative(lateThreshold) || isNegative(shiftMatchWindow) || isNegative(staleAfter)) {
            throw new IllegalStateException("app.time-tracking.late-threshold, shift-match-window and stale-after"
                    + " must be set and must not be negative");
        }
    }

    private static boolean isNegative(Duration duration) {
        return duration == null || duration.isNegative();
    }
}

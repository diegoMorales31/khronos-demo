package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.exceptions.InvalidRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * The optional from and to dates of a listing, both inclusive. A missing date leaves that side open.
 */
record DateRange(LocalDate from, LocalDate to) {

    DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
    }

    // Reports need both dates and cover at most maxDays days, counting both ends
    static DateRange bounded(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null) {
            throw new InvalidRequestException("'from' and 'to' are required");
        }
        DateRange range = new DateRange(from, to);
        if (ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            throw new InvalidRequestException("The date range cannot be longer than " + maxDays + " days");
        }
        return range;
    }

    /**
     * Listings that would otherwise return the whole history cover at most maxDays days, counting both
     * ends, like reports, but the dates stay optional. Without either, the range is the maxDays days up to
     * today, which is only asked for then. With one, the other is filled in maxDays days away from it.
     * With both, a longer range answers 400.
     */
    static DateRange capped(LocalDate from, LocalDate to, int maxDays, Supplier<LocalDate> today) {
        LocalDate last = from == null && to == null ? today.get() : to;
        if (from == null) {
            return bounded(last.minusDays(maxDays - 1), last, maxDays);
        }
        return bounded(from, last != null ? last : from.plusDays(maxDays - 1), maxDays);
    }

    LocalDateTime startInclusive() {
        return from != null ? from.atStartOfDay() : null;
    }

    // Midnight after the last day, so everything that happens on 'to' is still included
    LocalDateTime endExclusive() {
        return to != null ? to.plusDays(1).atStartOfDay() : null;
    }
}

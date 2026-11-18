package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.enums.WorkDayStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The shifts and counted (not voided) entries of one worker over a report's range, or over a single
 * day, and the figures the reports show for them. Shifts are planned in local time, so comparing them
 * with clock-in instants needs the establishment's time zone.
 */
final class WorkTally {

    private static final Comparator<TimeEntry> FIRST_CLOCK_IN = Comparator.comparing(TimeEntry::getClockIn)
            .thenComparing(TimeEntry::getId);

    private final ZoneId zone;
    private final List<WorkerShift> shifts = new ArrayList<>();
    private final List<TimeEntry> entries = new ArrayList<>();

    WorkTally(ZoneId zone) {
        this.zone = zone;
    }

    void add(WorkerShift shift) {
        shifts.add(shift);
    }

    void add(TimeEntry entry) {
        entries.add(entry);
    }

    List<WorkerShift> shifts() {
        return shifts;
    }

    List<TimeEntry> entries() {
        return entries;
    }

    boolean isEmpty() {
        return shifts.isEmpty() && entries.isEmpty();
    }

    long plannedMinutes() {
        return shifts.stream().mapToLong(WorkerShift::getDurationMinutes).sum();
    }

    // Open entries have no worked minutes yet
    long workedMinutes() {
        return entries.stream().map(TimeEntry::getWorkedMinutes).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    }

    // Planned minutes by the shift's date, for costs
    Map<LocalDate, Long> plannedMinutesByDay() {
        return shifts.stream().collect(Collectors.groupingBy(WorkerShift::getDate, TreeMap::new,
                Collectors.summingLong(WorkerShift::getDurationMinutes)));
    }

    // Worked minutes by the entry's business date, for costs
    Map<LocalDate, Long> workedMinutesByDay() {
        return entries.stream().filter(entry -> entry.getWorkedMinutes() != null)
                .collect(Collectors.groupingBy(TimeEntry::getBusinessDate, TreeMap::new,
                        Collectors.summingLong(entry -> entry.getWorkedMinutes())));
    }

    // Late shifts, not late entries: a shift is late when its first entry clocked in after the threshold.
    // Someone who steps out and clocks in again during the same shift is not late a second time. The
    // entries here are never voided, and a shift's entries all share its date, so they are all here.
    int lateCount(Duration lateThreshold) {
        Map<Long, TimeEntry> firstEntryByShift = entries.stream()
                .filter(entry -> entry.getShift() != null)
                .collect(Collectors.toMap(entry -> entry.getShift().getId(), Function.identity(),
                        BinaryOperator.minBy(FIRST_CLOCK_IN)));
        return (int) firstEntryByShift.values().stream().filter(entry -> isLate(entry, lateThreshold)).count();
    }

    // Shifts that have ended without any counted entry matched to them
    int missedShiftsCount(Instant now) {
        Set<Long> matched = matchedShiftIds();
        return (int) shifts.stream().filter(shift -> hasEnded(shift, now) && !matched.contains(shift.getId())).count();
    }

    int unplannedCount() {
        return (int) entries.stream().filter(entry -> entry.getShift() == null).count();
    }

    int openEntriesCount() {
        return (int) entries.stream().filter(TimeEntry::isOpen).count();
    }

    // The contract's order: OPEN, MISSED, UNPLANNED, LATE, UPCOMING, OK. Each shift and entry of the day
    // counts on its own, as in the hours report: a day with one shift worked and another missed is MISSED,
    // and an entry without a shift makes the day UNPLANNED even when its shifts were worked.
    WorkDayStatus dayStatus(Instant now, Duration lateThreshold) {
        if (openEntriesCount() > 0) {
            return WorkDayStatus.OPEN;
        }
        if (missedShiftsCount(now) > 0) {
            return WorkDayStatus.MISSED;
        }
        if (unplannedCount() > 0) {
            return WorkDayStatus.UNPLANNED;
        }
        if (lateCount(lateThreshold) > 0) {
            return WorkDayStatus.LATE;
        }
        Set<Long> matched = matchedShiftIds();
        return shifts.stream().anyMatch(shift -> !hasEnded(shift, now) && !matched.contains(shift.getId()))
                ? WorkDayStatus.UPCOMING
                : WorkDayStatus.OK;
    }

    private Set<Long> matchedShiftIds() {
        return entries.stream()
                .filter(entry -> entry.getShift() != null)
                .map(entry -> entry.getShift().getId())
                .collect(Collectors.toSet());
    }

    private boolean isLate(TimeEntry entry, Duration lateThreshold) {
        WorkerShift shift = entry.getShift();
        if (shift == null) {
            return false;
        }
        Instant shiftStart = shift.getStartDateTime().atZone(zone).toInstant();
        return entry.getClockIn().isAfter(shiftStart.plus(lateThreshold));
    }

    private boolean hasEnded(WorkerShift shift, Instant now) {
        return !shift.getEndDateTime().atZone(zone).toInstant().isAfter(now);
    }
}

package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.config.TimeTrackingProperties;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches time entries to planned shifts. Shifts are planned in local time, so the clock-in instant is
 * first turned into a local date-time with the establishment's time zone.
 */
@Component
public class TimeEntryShiftLinker {

    private final WorkerShiftRepository workerShiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeTrackingProperties properties;

    public TimeEntryShiftLinker(WorkerShiftRepository workerShiftRepository, TimeEntryRepository timeEntryRepository,
                                TimeTrackingProperties properties) {
        this.workerShiftRepository = workerShiftRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.properties = properties;
    }

    /**
     * Links the entry to the worker's shift whose interval, widened by the match window (3 hours) before
     * its start, contains the clock-in, and sets the business date to that shift's date. A clock-in at
     * 00:30 on Saturday during a Friday 18:00-02:00 shift counts for Friday. With no such shift the
     * business date is the local date of the clock-in.
     */
    public void link(TimeEntry entry) {
        ZoneId zone = entry.getEstablishment().getZoneId();
        LocalDateTime clockIn = LocalDateTime.ofInstant(entry.getClockIn(), zone);
        WorkerShift shift = workerShiftRepository.findShiftsForClockIn(entry.getWorker().getUserId(), clockIn,
                        clockIn.plus(properties.shiftMatchWindow())).stream()
                .findFirst()
                .orElse(null);
        entry.setShift(shift);
        entry.setBusinessDate(shift != null ? shift.getDate() : clockIn.toLocalDate());
    }

    /**
     * After shifts are created, their workers' entries that no shift was matched to, and whose clock-in
     * falls in the window of one of the new shifts, are linked to it and take its date, as if the shift
     * had existed at the clock-in: a clock-in at 00:30 on Saturday counts for a Friday 18:00-02:00 shift
     * entered afterwards. An entry in the window of two new shifts goes to the one that starts first, as
     * in {@link #link}. Voided entries are left alone. One query reads the entries for the whole batch.
     */
    public void linkToNewShifts(List<WorkerShift> shifts) {
        if (shifts.isEmpty()) {
            return;
        }
        List<MatchWindow> windows = shifts.stream().map(this::matchWindow).toList();
        Instant from = windows.stream().map(MatchWindow::start).min(Comparator.naturalOrder()).orElseThrow();
        Instant to = windows.stream().map(MatchWindow::end).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> workerIds = shifts.stream().map(shift -> shift.getWorker().getUserId()).collect(Collectors.toSet());

        for (TimeEntry entry : timeEntryRepository.findUnmatchedByWorkers(workerIds, from, to)) {
            shifts.stream()
                    .filter(shift -> shift.getWorker().getUserId().equals(entry.getWorker().getUserId())
                            && matchWindow(shift).contains(entry.getClockIn()))
                    .min(Comparator.comparing(WorkerShift::getStartDateTime))
                    .ifPresent(shift -> {
                        entry.setShift(shift);
                        entry.setBusinessDate(shift.getDate());
                    });
        }
    }

    /**
     * After a shift is edited, its entries that no longer fit it (it now belongs to another worker, or
     * the clock-in falls outside its new window) lose the link and keep their business date, as when a
     * shift is deleted. The entries that still fit follow the shift's date.
     */
    public void relinkAfterShiftChange(WorkerShift shift) {
        MatchWindow window = matchWindow(shift);
        timeEntryRepository.unlinkFromShift(shift.getId(), shift.getWorker().getUserId(), window.start(), window.end());
        timeEntryRepository.moveToBusinessDate(shift.getId(), shift.getDate());
    }

    // The clock-ins a shift matches, as instants: from the match window before its start until its end
    private MatchWindow matchWindow(WorkerShift shift) {
        ZoneId zone = shift.getWorker().getEstablishment().getZoneId();
        return new MatchWindow(shift.getStartDateTime().minus(properties.shiftMatchWindow()).atZone(zone).toInstant(),
                shift.getEndDateTime().atZone(zone).toInstant());
    }

    private record MatchWindow(Instant start, Instant end) {

        boolean contains(Instant instant) {
            return !instant.isBefore(start) && instant.isBefore(end);
        }
    }
}

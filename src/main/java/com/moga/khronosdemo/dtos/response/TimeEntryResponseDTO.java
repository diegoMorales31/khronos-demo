package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.TimeEntrySource;
import com.moga.khronosdemo.entities.enums.TimeEntryStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TimeEntryResponseDTO(
        Long id,
        Long workerId,
        String workerFirstName,
        String workerLastName,
        Long establishmentId,
        // The day of service the work counts for
        LocalDate businessDate,
        Instant clockIn,
        // Null while the worker is still clocked in
        Instant clockOut,
        int breakMinutes,
        // (clockOut - clockIn) - breakMinutes, in whole minutes; null while open
        Integer workedMinutes,
        // The planned shift the clock-in was matched to, if any
        Long shiftId,
        TimeEntrySource source,
        TimeEntryStatus status,
        // Corrected at least once after it was created
        Boolean isEdited,
        // Open for longer than app.time-tracking.stale-after (16 hours by default): probably a forgotten clock-out
        Boolean isStale,
        String note
) {
}

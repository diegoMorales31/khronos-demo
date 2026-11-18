package com.moga.khronosdemo.dtos.response;

import java.math.BigDecimal;

// One worker's row of the hours report. Minutes are whole minutes; money is in euros with two decimals.
public record WorkerHoursResponseDTO(
        Long workerId,
        String firstName,
        String lastName,
        Boolean isActive,
        // Sum of the planned shifts dated in the range
        long plannedMinutes,
        // Sum of the closed, not voided entries whose business date is in the range
        long workedMinutes,
        // workedMinutes - plannedMinutes
        long differenceMinutes,
        int shiftsCount,
        // Entries that are not voided, open ones included
        int entriesCount,
        // Shifts whose first entry clocked in more than the late threshold after they started; at most one per shift
        int lateCount,
        // Shifts that have already ended with no entry matched to them
        int missedShiftsCount,
        // Entries not matched to any shift
        int unplannedCount,
        int openEntriesCount,
        // The rate in force today in the establishment's time zone; null without one
        BigDecimal currentHourlyRate,
        // Minutes of each day times the rate in force that day; null when some of those minutes have no rate
        BigDecimal plannedCost,
        BigDecimal actualCost
) {
}

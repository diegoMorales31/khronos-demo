package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.WorkDayStatus;

import java.time.LocalDate;
import java.util.List;

// One business day of a worker's daily report: the shifts dated that day and the entries that count for it
public record WorkDayResponseDTO(
        LocalDate date,
        long plannedMinutes,
        long workedMinutes,
        WorkDayStatus status,
        List<WorkerShiftResponseDTO> shifts,
        List<TimeEntryResponseDTO> entries
) {
}

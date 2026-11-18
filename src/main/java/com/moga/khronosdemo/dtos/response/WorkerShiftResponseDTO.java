package com.moga.khronosdemo.dtos.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkerShiftResponseDTO(
        Long id,
        Long workerId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long zoneId,
        // True when the shift finishes on the day after its date, as in 18:00-02:00
        boolean endsNextDay,
        long durationMinutes
) {
}

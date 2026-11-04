package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.WorkZone;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkerShiftResponseDTO(
        Long id,
        Long workerId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        WorkZone eventZone
) {
}

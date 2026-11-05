package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkerShiftRequestDTO(
        @NotNull
        Long workerId,

        @NotNull
        LocalDate date,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        @NotNull
        Long zoneId
) {
}

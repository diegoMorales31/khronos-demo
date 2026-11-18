package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// Replaces the entry's times; a null clockOut leaves (or puts) the worker clocked in
public record TimeEntryCorrectionRequestDTO(
        @NotNull
        Instant clockIn,

        Instant clockOut,

        @NotNull @Min(0) @Max(600)
        Integer breakMinutes,

        @NotBlank @Size(min = 3, max = 255)
        String reason
) {
}

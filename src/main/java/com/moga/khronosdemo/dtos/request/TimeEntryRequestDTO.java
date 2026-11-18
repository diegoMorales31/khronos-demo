package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// An owner or admin records an entry for a worker: a clock-in now (no clockOut) or a past shift
public record TimeEntryRequestDTO(
        @NotNull
        Long workerId,

        @NotNull
        Instant clockIn,

        Instant clockOut,

        // 0 when left out
        @Min(0) @Max(600)
        Integer breakMinutes,

        @NotBlank @Size(min = 3, max = 255)
        String reason
) {
}

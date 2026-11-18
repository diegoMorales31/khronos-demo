package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// The clock-out time is the server's, never the client's
public record ClockOutRequestDTO(
        // Unpaid break taken during the shift; when left out the entry keeps its current break (0 by default)
        @Min(0) @Max(600)
        Integer breakMinutes,

        // Replaces the entry's note when given
        @Size(max = 255)
        String note
) {
}

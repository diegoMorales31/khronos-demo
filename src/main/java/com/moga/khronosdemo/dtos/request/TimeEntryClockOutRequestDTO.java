package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// An owner or admin clocks out a worker's open entry. The clock-out time is the server's, never the client's
public record TimeEntryClockOutRequestDTO(
        // Unpaid break taken during the shift; when left out the entry keeps its current break (0 by default)
        @Min(0) @Max(600)
        Integer breakMinutes,

        @NotBlank @Size(min = 3, max = 255)
        String reason
) {
}

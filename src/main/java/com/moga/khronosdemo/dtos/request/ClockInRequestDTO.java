package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Size;

// The clock-in time is the server's, never the client's
public record ClockInRequestDTO(
        @Size(max = 255)
        String note
) {
}

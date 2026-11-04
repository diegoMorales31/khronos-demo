package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotNull;

public record WorkerEventRequestDTO(
        @NotNull
        Long workerId,

        @NotNull
        Long eventId
) {
}

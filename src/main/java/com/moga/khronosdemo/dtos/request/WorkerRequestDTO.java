package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotNull;

public record WorkerRequestDTO(
        @NotNull
        Long userId,

        @NotNull
        Long establishmentId,

        @NotNull
        Boolean isActive
) {
}

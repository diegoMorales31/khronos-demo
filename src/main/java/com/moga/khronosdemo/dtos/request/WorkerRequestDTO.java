package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.WorkZone;
import jakarta.validation.constraints.NotNull;

public record WorkerRequestDTO(
        @NotNull
        Long userId,

        @NotNull
        Boolean isActive,

        @NotNull
        WorkZone workZone
) {
}

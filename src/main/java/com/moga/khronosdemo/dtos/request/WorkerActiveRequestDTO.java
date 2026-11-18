package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotNull;

public record WorkerActiveRequestDTO(
        @NotNull
        Boolean active
) {
}

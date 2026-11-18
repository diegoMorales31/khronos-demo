package com.moga.khronosdemo.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record WorkerWithUserRequestDTO(
        @NotNull
        Long establishmentId,

        @NotNull @Valid
        UserRequestDTO user
) {
}

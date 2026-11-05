package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ZoneRequestDTO(
        @NotBlank @Size(max = 50)
        String name,

        @NotNull
        Long establishmentId
) {
}

package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EstablishmentRequestDTO(
        @NotBlank @Size(max = 100)
        String establishmentName,

        @NotBlank @Size(max = 255)
        String establishmentAddress,

        @NotNull
        Long ownerId
) {
}

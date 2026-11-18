package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.TimeZoneId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EstablishmentRequestDTO(
        @NotBlank @Size(max = 100)
        String establishmentName,

        @NotBlank @Size(max = 255)
        String establishmentAddress,

        @NotNull
        Long ownerId,

        // Optional. A new establishment defaults to Europe/Madrid; an update without it keeps the current zone
        @TimeZoneId
        String timeZone
) {
}

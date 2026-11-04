package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.WorkZone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventRequestDTO(
        @NotNull
        LocalDateTime dateTime,

        @NotNull
        WorkZone workZone,

        @NotNull @Positive
        Integer peopleNumber,

        @NotNull
        Long establishmentId,

        @Size(max = 500)
        String observation
) {
}

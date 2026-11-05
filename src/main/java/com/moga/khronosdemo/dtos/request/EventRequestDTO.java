package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventRequestDTO(

        @NotBlank @Size(max = 100)
        String title,

        @NotNull
        LocalDateTime dateTime,

        @NotNull
        Long zoneId,

        @NotNull @Positive
        Integer peopleNumber,

        @Size(max = 500)
        String description
) {
}

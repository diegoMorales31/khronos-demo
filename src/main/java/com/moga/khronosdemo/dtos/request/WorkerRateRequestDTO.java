package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkerRateRequestDTO(
        // Euros per hour, with at most two decimals
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal hourlyRate,

        @NotNull
        LocalDate effectiveFrom
) {
}

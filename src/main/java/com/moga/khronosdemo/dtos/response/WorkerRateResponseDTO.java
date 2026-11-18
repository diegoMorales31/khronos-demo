package com.moga.khronosdemo.dtos.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WorkerRateResponseDTO(
        Long id,
        Long workerId,
        BigDecimal hourlyRate,
        LocalDate effectiveFrom,
        Instant createdAt
) {
}

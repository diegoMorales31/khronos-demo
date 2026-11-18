package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.LaborCostSource;

import java.math.BigDecimal;
import java.time.LocalDate;

// The period before the summary's, to compare with: worked out exactly like the summary's totals
public record SummaryPeriodResponseDTO(
        LocalDate from,
        LocalDate to,
        BigDecimal sales,
        BigDecimal expenses,
        BigDecimal laborCost,
        BigDecimal grossMargin,
        // Which staff cost this period's grossMargin took off, decided on this period's own laborCost
        LaborCostSource laborCostSource
) {
}

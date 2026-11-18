package com.moga.khronosdemo.dtos.response;

import java.time.LocalDate;
import java.util.List;

// The business at a glance between from and to, both included. Amounts are euros with two decimals
public record BusinessSummaryResponseDTO(
        LocalDate from,
        LocalDate to,
        String currency,
        SummaryTotalsResponseDTO totals,
        // One per day of the range, earliest first
        List<SummaryDayResponseDTO> days,
        // Only categories with expenses, largest first
        List<CategoryAmountResponseDTO> expensesByCategory,
        // The same number of days right before 'from'
        SummaryPeriodResponseDTO previous
) {
}

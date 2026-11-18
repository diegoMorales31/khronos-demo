package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.DayClosingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

// One business day of the summary. margin is worked out like the totals' grossMargin, with the day's own
// figures: a null laborCost makes it take off the day's PAYROLL expenses instead
public record SummaryDayResponseDTO(
        LocalDate date,
        // The total sales of the day's closing, or 0 without one
        BigDecimal sales,
        // Every expense of the day, PAYROLL included
        BigDecimal expenses,
        // Null when the period's laborCost is null
        BigDecimal laborCost,
        BigDecimal margin,
        DayClosingStatus closingStatus,
        // The difference of the day's closing; null without one, or while its cash has not been counted
        BigDecimal cashDifference
) {
}

package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.LaborCostSource;

import java.math.BigDecimal;

public record SummaryTotalsResponseDTO(
        // From the cash closings of the range, drafts included
        BigDecimal sales,
        BigDecimal cashSales,
        BigDecimal cardSales,
        BigDecimal otherSales,
        BigDecimal tips,
        // Every expense of the range
        BigDecimal expenses,
        // The PAYROLL part of those expenses
        BigDecimal payrollExpenses,
        // Hours worked times the rate in force each day, as in the hours report; null when no worker's cost is known
        BigDecimal laborCost,
        // True when laborCost is known but leaves out a worker who worked in the range with an unknown cost
        boolean laborCostPartial,
        // The workers who worked in the range and whose cost is unknown (a day without a rate), left out of laborCost
        int workersWithoutCost,
        // Hours of the shifts times the rate; null when no worker's cost is known
        BigDecimal plannedLaborCost,
        // sales - (expenses - payrollExpenses) - the staff cost: laborCost, or payrollExpenses when laborCost is
        // null (see laborCostSource); negative when the period lost money
        BigDecimal grossMargin,
        // Which staff cost grossMargin took off: HOURS (laborCost) or PAYROLL_EXPENSES (laborCost is null)
        LaborCostSource laborCostSource,
        // laborCost / sales x 100, one decimal; null when sales are 0 or laborCost is unknown
        BigDecimal laborCostPercent,
        // The differences of the range's CLOSED days added up; drafts can still change, so they are left out
        BigDecimal cashDifference,
        // The differences the range's drafts show right now, added up: provisional until they are closed. A
        // draft whose cash has not been counted adds nothing
        BigDecimal draftCashDifference,
        int closedDays,
        int draftDays,
        // Days before today with a shift or a time entry and no closing
        int missingClosingDays
) {
}

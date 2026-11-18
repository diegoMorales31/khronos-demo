package com.moga.khronosdemo.dtos.response;

import java.math.BigDecimal;

// Sums over every worker of the report. Costs add up the workers whose cost is known, and are null if none is
public record HoursTotalsResponseDTO(
        long plannedMinutes,
        long workedMinutes,
        long differenceMinutes,
        BigDecimal plannedCost,
        BigDecimal actualCost,
        // True when actualCost is known but leaves out a worker who worked in the range with an unknown cost
        boolean costPartial,
        // The workers who worked in the range and whose actual cost is unknown (a day without a rate)
        int workersWithoutCost
) {
}

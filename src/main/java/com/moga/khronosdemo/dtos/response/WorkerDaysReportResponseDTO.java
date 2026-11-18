package com.moga.khronosdemo.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A worker's report day by day. Only days with a shift or an entry are listed. Costs are null for a worker reading their own
public record WorkerDaysReportResponseDTO(
        Long workerId,
        String firstName,
        String lastName,
        LocalDate from,
        LocalDate to,
        long plannedMinutes,
        long workedMinutes,
        long differenceMinutes,
        BigDecimal plannedCost,
        BigDecimal actualCost,
        List<WorkDayResponseDTO> days
) {
}

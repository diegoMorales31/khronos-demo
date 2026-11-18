package com.moga.khronosdemo.dtos.response;

import java.time.LocalDate;
import java.util.List;

public record HoursReportResponseDTO(
        LocalDate from,
        LocalDate to,
        String currency,
        List<WorkerHoursResponseDTO> workers,
        HoursTotalsResponseDTO totals
) {
}

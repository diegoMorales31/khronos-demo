package com.moga.khronosdemo.dtos.response;

import java.util.List;

public record ShiftsWithEventsResponseDTO(
        List<WorkerShiftResponseDTO> shifts,
        List<WorkerEventResponseDTO> assignments
) {
}

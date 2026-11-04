package com.moga.khronosdemo.dtos.response;

public record WorkerEventResponseDTO(
        Long id,
        Long workerId,
        Long eventId
) {
}

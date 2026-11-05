package com.moga.khronosdemo.dtos.response;

public record WorkerResponseDTO(
        Long userId,
        Long establishmentId,
        Boolean isActive
) {
}

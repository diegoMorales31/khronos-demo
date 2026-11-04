package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.WorkZone;

public record WorkerResponseDTO(
        Long id,
        Long userId,
        Boolean isActive,
        WorkZone workZone
) {
}

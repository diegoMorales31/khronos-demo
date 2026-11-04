package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.WorkZone;

import java.time.LocalDateTime;

public record EventResponseDTO(
        Long id,
        LocalDateTime dateTime,
        WorkZone workZone,
        Integer peopleNumber,
        Long establishmentId,
        String observation
) {
}

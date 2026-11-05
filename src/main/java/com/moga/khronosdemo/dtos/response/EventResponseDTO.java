package com.moga.khronosdemo.dtos.response;

import java.time.LocalDateTime;

public record EventResponseDTO(
        Long id,
        String title,
        LocalDateTime dateTime,
        Long zoneId,
        String zoneName,
        Integer peopleNumber,
        Long establishmentId,
        String description
) {
}

package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.EmailStatus;

import java.time.Instant;

public record EmailResponseDTO(
        Long id,
        String recipient,
        String subject,
        EmailStatus status,
        String resendId,
        String errorMessage,
        Instant createdAt
) {
}

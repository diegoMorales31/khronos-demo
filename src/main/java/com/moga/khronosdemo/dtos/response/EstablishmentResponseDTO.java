package com.moga.khronosdemo.dtos.response;

public record EstablishmentResponseDTO(
        Long id,
        String establishmentName,
        String establishmentAddress,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String ownerPhone
) {
}

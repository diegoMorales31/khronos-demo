package com.moga.khronosdemo.dtos.response;

public record EstablishmentResponseDTO(
        Long id,
        String establishmentName,
        String establishmentAddress,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String ownerPhone,
        String timeZone
) {

    // Workers can see who runs their venue, but not the owner's personal contact details
    public EstablishmentResponseDTO withoutOwnerContact() {
        return new EstablishmentResponseDTO(id, establishmentName, establishmentAddress, ownerId, ownerName, null, null,
                timeZone);
    }
}

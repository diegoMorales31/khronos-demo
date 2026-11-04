package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import org.springframework.stereotype.Component;

@Component
public class EstablishmentMapper {

    public EstablishmentResponseDTO toResponse(Establishment establishment) {
        if (establishment == null) {
            return null;
        }
        return new EstablishmentResponseDTO(
                establishment.getId(),
                establishment.getEstablishmentName(),
                establishment.getEstablishmentAddress(),
                establishment.getOwnerName(),
                establishment.getOwnerEmail(),
                establishment.getOwnerPhone()
        );
    }

    public Establishment toEntity(EstablishmentRequestDTO request) {
        if (request == null) {
            return null;
        }
        Establishment establishment = new Establishment();
        updateEntity(establishment, request);
        return establishment;
    }

    public void updateEntity(Establishment establishment, EstablishmentRequestDTO request) {
        establishment.setEstablishmentName(request.establishmentName());
        establishment.setEstablishmentAddress(request.establishmentAddress());
        establishment.setOwnerName(request.ownerName());
        establishment.setOwnerEmail(request.ownerEmail());
        establishment.setOwnerPhone(request.ownerPhone());
    }
}

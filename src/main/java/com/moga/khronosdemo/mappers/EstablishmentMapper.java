package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import org.springframework.stereotype.Component;

@Component
public class EstablishmentMapper {

    public EstablishmentResponseDTO toResponse(Establishment establishment) {
        if (establishment == null) {
            return null;
        }
        User owner = establishment.getOwner();
        return new EstablishmentResponseDTO(
                establishment.getId(),
                establishment.getEstablishmentName(),
                establishment.getEstablishmentAddress(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getFirstName() + " " + owner.getLastName() : null,
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getPhoneNumber() : null
        );
    }

    public Establishment toEntity(EstablishmentRequestDTO request, User owner) {
        if (request == null) {
            return null;
        }
        Establishment establishment = new Establishment();
        updateEntity(establishment, request, owner);
        return establishment;
    }

    public void updateEntity(Establishment establishment, EstablishmentRequestDTO request, User owner) {
        establishment.setEstablishmentName(request.establishmentName());
        establishment.setEstablishmentAddress(request.establishmentAddress());
        establishment.setOwner(owner);
    }
}

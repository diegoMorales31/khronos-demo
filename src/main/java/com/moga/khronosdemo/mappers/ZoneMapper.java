package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.ZoneRequestDTO;
import com.moga.khronosdemo.dtos.response.ZoneResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Zone;
import org.springframework.stereotype.Component;

@Component
public class ZoneMapper {

    public ZoneResponseDTO toResponse(Zone zone) {
        if (zone == null) {
            return null;
        }
        return new ZoneResponseDTO(
                zone.getId(),
                zone.getName(),
                zone.getEstablishment() != null ? zone.getEstablishment().getId() : null
        );
    }

    public Zone toEntity(ZoneRequestDTO request, Establishment establishment) {
        if (request == null) {
            return null;
        }
        Zone zone = new Zone();
        updateEntity(zone, request, establishment);
        return zone;
    }

    public void updateEntity(Zone zone, ZoneRequestDTO request, Establishment establishment) {
        zone.setName(request.name());
        zone.setEstablishment(establishment);
    }
}

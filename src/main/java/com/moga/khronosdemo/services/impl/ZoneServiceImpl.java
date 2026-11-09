package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ZoneRequestDTO;
import com.moga.khronosdemo.dtos.response.ZoneResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.ZoneMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.services.ZoneService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;
    private final EstablishmentRepository establishmentRepository;
    private final ZoneMapper zoneMapper;

    public ZoneServiceImpl(ZoneRepository zoneRepository, EstablishmentRepository establishmentRepository,
                           ZoneMapper zoneMapper) {
        this.zoneRepository = zoneRepository;
        this.establishmentRepository = establishmentRepository;
        this.zoneMapper = zoneMapper;
    }

    @Override
    public ZoneResponseDTO create(ZoneRequestDTO request) {

        Establishment establishment = establishmentRepository.findById(request.establishmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", request.establishmentId()));

        if (zoneRepository.existsByNameAndEstablishment_Id(request.name(), request.establishmentId())) {
            throw new BusinessRuleException("A zone with that name already exists in this establishment");
        }

        Zone zone = zoneMapper.toEntity(request, establishment);
        return zoneMapper.toResponse(zoneRepository.save(zone));

    }

    @Override
    @Transactional(readOnly = true)
    public ZoneResponseDTO findById(Long id) {
        return zoneMapper.toResponse(getZone(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponseDTO> findAll() {
        return zoneRepository.findAll().stream().map(zoneMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponseDTO> findByEstablishment(Long establishmentId) {
        return zoneRepository.findByEstablishment_Id(establishmentId).stream().map(zoneMapper::toResponse).toList();
    }

    @Override
    public ZoneResponseDTO update(Long id, ZoneRequestDTO request) {

        Zone zone = getZone(id);
        Establishment establishment = zone.getEstablishment();

        if (!establishment.getId().equals(request.establishmentId())) {
            throw new BusinessRuleException("A zone cannot be moved to another establishment");
        }
        if (zoneRepository.existsByNameAndEstablishment_IdAndIdNot(request.name(), request.establishmentId(), id)) {
            throw new BusinessRuleException("A zone with that name already exists in this establishment");
        }

        zoneMapper.updateEntity(zone, request, establishment);
        return zoneMapper.toResponse(zone);

    }

    @Override
    public void delete(Long id) {
        zoneRepository.delete(getZone(id));
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }
}

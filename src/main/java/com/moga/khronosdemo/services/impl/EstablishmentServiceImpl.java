package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Role;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.EstablishmentMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.EstablishmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EstablishmentServiceImpl implements EstablishmentService {

    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final EstablishmentMapper establishmentMapper;

    public EstablishmentServiceImpl(EstablishmentRepository establishmentRepository, UserRepository userRepository,
                                    EstablishmentMapper establishmentMapper) {
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.establishmentMapper = establishmentMapper;
    }

    @Override
    public EstablishmentResponseDTO create(EstablishmentRequestDTO request) {
        if (establishmentRepository.existsByEstablishmentAddress(request.establishmentAddress())) {
            throw new BusinessRuleException("An establishment already exists at that address");
        }
        Establishment establishment = establishmentMapper.toEntity(request, getOwner(request.ownerId()));
        return establishmentMapper.toResponse(establishmentRepository.save(establishment));
    }

    @Override
    @Transactional(readOnly = true)
    public EstablishmentResponseDTO findById(Long id) {
        return establishmentMapper.toResponse(getEstablishment(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstablishmentResponseDTO> findAll() {
        return establishmentRepository.findAll().stream().map(establishmentMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstablishmentResponseDTO> findByOwner(Long ownerId) {
        return establishmentRepository.findByOwner_Id(ownerId).stream().map(establishmentMapper::toResponse).toList();
    }

    @Override
    public EstablishmentResponseDTO update(Long id, EstablishmentRequestDTO request) {

        Establishment establishment = getEstablishment(id);

        if (establishmentRepository.existsByEstablishmentAddressAndIdNot(request.establishmentAddress(), id)) {
            throw new BusinessRuleException("An establishment already exists at that address");

        }
        establishmentMapper.updateEntity(establishment, request, getOwner(request.ownerId()));

        return establishmentMapper.toResponse(establishment);

    }

    @Override
    public void delete(Long id) {
        establishmentRepository.delete(getEstablishment(id));
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }

    private User getOwner(Long ownerId) {

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", ownerId));

        if (owner.getRole() != Role.OWNER) {
            throw new BusinessRuleException("The owner of an establishment must have the OWNER role");

        }

        return owner;

    }
}

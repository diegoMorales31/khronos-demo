package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;

import java.util.List;

public interface EstablishmentService {

    EstablishmentResponseDTO create(EstablishmentRequestDTO request);

    EstablishmentResponseDTO findById(Long id);

    List<EstablishmentResponseDTO> findAll();

    List<EstablishmentResponseDTO> findByOwner(Long ownerId);

    EstablishmentResponseDTO update(Long id, EstablishmentRequestDTO request);

    void delete(Long id);
}

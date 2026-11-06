package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ZoneRequestDTO;
import com.moga.khronosdemo.dtos.response.ZoneResponseDTO;

import java.util.List;

public interface ZoneService {

    ZoneResponseDTO create(ZoneRequestDTO request);

    ZoneResponseDTO findById(Long id);

    List<ZoneResponseDTO> findAll();

    List<ZoneResponseDTO> findByEstablishment(Long establishmentId);

    ZoneResponseDTO update(Long id, ZoneRequestDTO request);

    void delete(Long id);
}

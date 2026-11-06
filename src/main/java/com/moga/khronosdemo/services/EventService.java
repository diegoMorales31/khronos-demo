package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;

import java.util.List;

public interface EventService {

    EventResponseDTO create(EventRequestDTO request);

    EventResponseDTO findById(Long id);

    List<EventResponseDTO> findAll();

    List<EventResponseDTO> findByZone(Long zoneId);

    List<EventResponseDTO> findByEstablishment(Long establishmentId);

    EventResponseDTO update(Long id, EventRequestDTO request);

    void delete(Long id);
}

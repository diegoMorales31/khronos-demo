package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface EventService {

    List<EventResponseDTO> create(List<EventRequestDTO> requests);

    EventResponseDTO findById(Long id);

    List<EventResponseDTO> findAll();

    List<EventResponseDTO> findByZone(Long zoneId, LocalDate from, LocalDate to);

    List<EventResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to);

    EventResponseDTO update(Long id, EventRequestDTO request);

    void delete(Long id);
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface WorkerShiftService {

    List<WorkerShiftResponseDTO> create(List<WorkerShiftRequestDTO> request);

    WorkerShiftResponseDTO findById(Long id);

    List<WorkerShiftResponseDTO> findAll();

    List<WorkerShiftResponseDTO> findByOwner(Long ownerId, LocalDate from, LocalDate to);

    List<WorkerShiftResponseDTO> findByWorker(Long workerId, LocalDate from, LocalDate to);

    List<WorkerShiftResponseDTO> findByZone(Long zoneId, LocalDate from, LocalDate to);

    WorkerShiftResponseDTO update(Long id, WorkerShiftRequestDTO request);

    void delete(Long id);
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;

import java.util.List;

public interface WorkerShiftService {

    WorkerShiftResponseDTO create(WorkerShiftRequestDTO request);

    WorkerShiftResponseDTO findById(Long id);

    List<WorkerShiftResponseDTO> findAll();

    List<WorkerShiftResponseDTO> findByWorker(Long workerId);

    List<WorkerShiftResponseDTO> findByZone(Long zoneId);

    WorkerShiftResponseDTO update(Long id, WorkerShiftRequestDTO request);

    void delete(Long id);
}

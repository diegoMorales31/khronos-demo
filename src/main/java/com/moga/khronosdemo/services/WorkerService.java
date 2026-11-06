package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;

import java.util.List;

public interface WorkerService {

    WorkerResponseDTO create(WorkerRequestDTO request);

    WorkerResponseDTO findById(Long userId);

    List<WorkerResponseDTO> findAll();

    List<WorkerResponseDTO> findByEstablishment(Long establishmentId);

    WorkerResponseDTO update(Long userId, WorkerRequestDTO request);

    void delete(Long userId);
}

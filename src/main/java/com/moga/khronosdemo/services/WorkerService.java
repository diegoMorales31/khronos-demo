package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerWithUserRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerWithUserResponseDTO;

import java.util.List;

public interface WorkerService {

    WorkerResponseDTO create(WorkerRequestDTO request);

    WorkerWithUserResponseDTO createWithUser(WorkerWithUserRequestDTO request, Long creatorId);

    WorkerResponseDTO findById(Long userId);

    List<WorkerResponseDTO> findAll();

    List<WorkerResponseDTO> findByEstablishment(Long establishmentId);

    WorkerResponseDTO update(Long userId, WorkerRequestDTO request);

    void delete(Long userId);

    void changeActivationStatus(Long userId);

    WorkerResponseDTO setActive(Long userId, boolean active);

}

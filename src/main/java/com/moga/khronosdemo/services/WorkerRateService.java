package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerRateRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerRateResponseDTO;

import java.util.List;

public interface WorkerRateService {

    List<WorkerRateResponseDTO> findByWorker(Long workerId);

    WorkerRateResponseDTO create(Long workerId, WorkerRateRequestDTO request);

    void delete(Long workerId, Long rateId);
}

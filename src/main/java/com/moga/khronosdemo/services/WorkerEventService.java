package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;

import java.util.List;

public interface WorkerEventService {

    WorkerEventResponseDTO create(WorkerEventRequestDTO request);

    WorkerEventResponseDTO findById(Long id);

    List<WorkerEventResponseDTO> findByWorker(Long workerId);

    List<WorkerEventResponseDTO> findByEvent(Long eventId);

    void delete(Long id);
}

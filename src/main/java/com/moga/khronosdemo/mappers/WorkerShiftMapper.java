package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import org.springframework.stereotype.Component;

@Component
public class WorkerShiftMapper {

    public WorkerShiftResponseDTO toResponse(WorkerShift workerShift) {
        if (workerShift == null) {
            return null;
        }
        return new WorkerShiftResponseDTO(
                workerShift.getId(),
                workerShift.getWorker() != null ? workerShift.getWorker().getId() : null,
                workerShift.getDate(),
                workerShift.getStartTime(),
                workerShift.getEndTime(),
                workerShift.getEventZone()
        );
    }

    public WorkerShift toEntity(WorkerShiftRequestDTO request, Worker worker) {
        if (request == null) {
            return null;
        }
        WorkerShift workerShift = new WorkerShift();
        updateEntity(workerShift, request, worker);
        return workerShift;
    }

    public void updateEntity(WorkerShift workerShift, WorkerShiftRequestDTO request, Worker worker) {
        workerShift.setWorker(worker);
        workerShift.setDate(request.date());
        workerShift.setStartTime(request.startTime());
        workerShift.setEndTime(request.endTime());
        workerShift.setEventZone(request.eventZone());
    }
}

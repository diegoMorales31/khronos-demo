package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import org.springframework.stereotype.Component;

@Component
public class WorkerShiftMapper {

    public WorkerShiftResponseDTO toResponse(WorkerShift workerShift) {
        if (workerShift == null) {
            return null;
        }
        Zone zone = workerShift.getZone();
        return new WorkerShiftResponseDTO(
                workerShift.getId(),
                workerShift.getWorker() != null ? workerShift.getWorker().getUserId() : null,
                workerShift.getDate(),
                workerShift.getStartTime(),
                workerShift.getEndTime(),
                zone != null ? zone.getId() : null
        );
    }

    public WorkerShift toEntity(WorkerShiftRequestDTO request, Worker worker, Zone zone) {
        if (request == null) {
            return null;
        }
        WorkerShift workerShift = new WorkerShift();
        updateEntity(workerShift, request, worker, zone);
        return workerShift;
    }

    public void updateEntity(WorkerShift workerShift, WorkerShiftRequestDTO request, Worker worker, Zone zone) {
        workerShift.setWorker(worker);
        workerShift.setDate(request.date());
        workerShift.setStartTime(request.startTime());
        workerShift.setEndTime(request.endTime());
        workerShift.setZone(zone);
    }
}

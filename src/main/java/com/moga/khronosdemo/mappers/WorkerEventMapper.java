package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import org.springframework.stereotype.Component;

@Component
public class WorkerEventMapper {

    public WorkerEventResponseDTO toResponse(WorkerEvent workerEvent) {
        if (workerEvent == null) {
            return null;
        }
        return new WorkerEventResponseDTO(
                workerEvent.getId(),
                workerEvent.getWorker() != null ? workerEvent.getWorker().getUserId() : null,
                workerEvent.getEvent() != null ? workerEvent.getEvent().getId() : null
        );
    }

    public WorkerEvent toEntity(WorkerEventRequestDTO request, Worker worker, Event event) {
        if (request == null) {
            return null;
        }
        return new WorkerEvent(worker, event);
    }
}

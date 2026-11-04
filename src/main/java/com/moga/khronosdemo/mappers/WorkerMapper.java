package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import org.springframework.stereotype.Component;

@Component
public class WorkerMapper {

    public WorkerResponseDTO toResponse(Worker worker) {
        if (worker == null) {
            return null;
        }
        return new WorkerResponseDTO(
                worker.getId(),
                worker.getUser() != null ? worker.getUser().getId() : null,
                worker.getActive(),
                worker.getWorkZone()
        );
    }

    public Worker toEntity(WorkerRequestDTO request, User user) {
        if (request == null) {
            return null;
        }
        Worker worker = new Worker();
        updateEntity(worker, request, user);
        return worker;
    }

    public void updateEntity(Worker worker, WorkerRequestDTO request, User user) {
        worker.setUser(user);
        worker.setActive(request.isActive());
        worker.setWorkZone(request.workZone());
    }
}

package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
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
                worker.getUserId(),
                worker.getEstablishment() != null ? worker.getEstablishment().getId() : null,
                worker.getActive()
        );
    }

    public Worker toEntity(WorkerRequestDTO request, User user, Establishment establishment) {
        if (request == null) {
            return null;
        }
        Worker worker = new Worker(user, establishment);
        worker.setActive(request.isActive());
        return worker;
    }

    public void updateEntity(Worker worker, WorkerRequestDTO request, Establishment establishment) {
        worker.setEstablishment(establishment);
        worker.setActive(request.isActive());
    }
}

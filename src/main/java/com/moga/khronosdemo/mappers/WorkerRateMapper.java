package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.WorkerRateRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerRateResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WorkerRateMapper {

    public WorkerRateResponseDTO toResponse(WorkerRate rate) {
        if (rate == null) {
            return null;
        }
        return new WorkerRateResponseDTO(
                rate.getId(),
                rate.getWorker() != null ? rate.getWorker().getUserId() : null,
                rate.getHourlyRate(),
                rate.getEffectiveFrom(),
                rate.getCreatedAt()
        );
    }

    // The amount is stored with two decimals, so 12.5 comes back as 12.50 straight away
    public WorkerRate toEntity(WorkerRateRequestDTO request, Worker worker, Instant createdAt) {
        if (request == null) {
            return null;
        }
        return new WorkerRate(worker, request.hourlyRate().setScale(2), request.effectiveFrom(), createdAt);
    }
}

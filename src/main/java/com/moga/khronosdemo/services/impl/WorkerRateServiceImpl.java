package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerRateRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerRateResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerRateMapper;
import com.moga.khronosdemo.repositories.WorkerRateRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.WorkerRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class WorkerRateServiceImpl implements WorkerRateService {

    private final WorkerRateRepository workerRateRepository;
    private final WorkerRepository workerRepository;
    private final WorkerRateMapper workerRateMapper;
    private final Clock clock;

    public WorkerRateServiceImpl(WorkerRateRepository workerRateRepository, WorkerRepository workerRepository,
                                 WorkerRateMapper workerRateMapper, Clock clock) {
        this.workerRateRepository = workerRateRepository;
        this.workerRepository = workerRepository;
        this.workerRateMapper = workerRateMapper;
        this.clock = clock;
    }

    // Most recent first, so the rate in force today is usually at the top
    @Override
    @Transactional(readOnly = true)
    public List<WorkerRateResponseDTO> findByWorker(Long workerId) {
        getWorker(workerId);
        return workerRateRepository.findByWorker_UserIdOrderByEffectiveFromDesc(workerId).stream()
                .map(workerRateMapper::toResponse)
                .toList();
    }

    // A rate is never edited: a pay change is a new rate from a later day. The unique constraint on
    // (worker_id, effective_from) still answers 409 if two requests add the same day at once.
    @Override
    public WorkerRateResponseDTO create(Long workerId, WorkerRateRequestDTO request) {
        Worker worker = getWorker(workerId);
        if (workerRateRepository.existsByWorker_UserIdAndEffectiveFrom(workerId, request.effectiveFrom())) {
            throw new BusinessRuleException("The worker already has a rate effective from " + request.effectiveFrom());
        }
        WorkerRate rate = workerRateMapper.toEntity(request, worker, clock.instant().truncatedTo(ChronoUnit.MICROS));
        return workerRateMapper.toResponse(workerRateRepository.save(rate));
    }

    // A rate in force has already priced days that were worked, and deleting it would change their cost
    // without a trace. Only a rate that starts after today, in the establishment's time zone, can go.
    @Override
    public void delete(Long workerId, Long rateId) {
        WorkerRate rate = workerRateRepository.findByIdAndWorker_UserId(rateId, workerId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerRate", rateId));
        LocalDate today = LocalDate.ofInstant(clock.instant(), rate.getWorker().getEstablishment().getZoneId());
        if (!rate.getEffectiveFrom().isAfter(today)) {
            throw new BusinessRuleException("Cannot delete a rate that has already taken effect; add a new rate"
                    + " from the day the change applies instead");
        }
        workerRateRepository.delete(rate);
    }

    private Worker getWorker(Long id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
    }
}

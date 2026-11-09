package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerShiftMapper;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.services.WorkerShiftService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkerShiftServiceImpl implements WorkerShiftService {

    private final WorkerShiftRepository workerShiftRepository;
    private final WorkerRepository workerRepository;
    private final ZoneRepository zoneRepository;
    private final WorkerShiftMapper workerShiftMapper;

    public WorkerShiftServiceImpl(WorkerShiftRepository workerShiftRepository, WorkerRepository workerRepository,
                                  ZoneRepository zoneRepository, WorkerShiftMapper workerShiftMapper) {
        this.workerShiftRepository = workerShiftRepository;
        this.workerRepository = workerRepository;
        this.zoneRepository = zoneRepository;
        this.workerShiftMapper = workerShiftMapper;
    }

    @Override
    public WorkerShiftResponseDTO create(WorkerShiftRequestDTO request) {

        Worker worker = getWorker(request.workerId());
        Zone zone = getZone(request.zoneId());

        validate(request, worker, zone, null);

        WorkerShift workerShift = workerShiftMapper.toEntity(request, worker, zone);

        return workerShiftMapper.toResponse(workerShiftRepository.save(workerShift));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerShiftResponseDTO findById(Long id) {
        return workerShiftMapper.toResponse(getWorkerShift(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findAll() {
        return workerShiftRepository.findAll().stream().map(workerShiftMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findByOwner(Long ownerId) {
        /* return workerShiftRepository.findByWorker_Establishment_Owner_IdOrderByDateAscStartTimeAsc(ownerId).stream()
                .map(workerShiftMapper::toResponse)
                .toList();
        */

        return workerShiftRepository.findAll().stream().filter(w ->
                        w.getWorker().getEstablishment().getOwner().getId()
                .equals(ownerId))
                .map(workerShiftMapper::toResponse).toList();

    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findByWorker(Long workerId) {
        //return workerShiftRepository.findByWorker_UserId(workerId).stream().map(workerShiftMapper::toResponse).toList();

        return workerShiftRepository.findAll().stream().filter(w -> w.getWorker().getUserId().equals(workerId)).map(workerShiftMapper::toResponse).toList();

    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findByZone(Long zoneId) {
        //return workerShiftRepository.findByZone_Id(zoneId).stream().map(workerShiftMapper::toResponse).toList();

        return workerShiftRepository.findAll().stream()
                .filter(w -> w.getZone().getId().equals(zoneId))
                .map(workerShiftMapper::toResponse)
                .toList();

    }

    @Override
    public WorkerShiftResponseDTO update(Long id, WorkerShiftRequestDTO request) {

        WorkerShift workerShift = getWorkerShift(id);
        Worker worker = getWorker(request.workerId());
        Zone zone = getZone(request.zoneId());

        validate(request, worker, zone, id);

        workerShiftMapper.updateEntity(workerShift, request, worker, zone);

        return workerShiftMapper.toResponse(workerShift);

    }

    @Override
    public void delete(Long id) {
        workerShiftRepository.delete(getWorkerShift(id));
    }

    private void validate(WorkerShiftRequestDTO request, Worker worker, Zone zone, Long shiftId) {

        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("End time must be after start time");
        }

        if (!zone.getEstablishment().getId().equals(worker.getEstablishment().getId())) {
            throw new BusinessRuleException("The zone does not belong to the worker's establishment");
        }

        if (workerShiftRepository.existsOverlappingShift(worker.getUserId(), request.date(),
                request.startTime(), request.endTime(), shiftId)) {
            throw new BusinessRuleException("The worker already has a shift that overlaps with "
                    + request.startTime() + "-" + request.endTime() + " on " + request.date());
        }

    }

    private WorkerShift getWorkerShift(Long id) {
        return workerShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerShift", id));
    }

    private Worker getWorker(Long id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }
}

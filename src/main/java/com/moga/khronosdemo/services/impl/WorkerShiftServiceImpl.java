package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerShiftMapper;
import com.moga.khronosdemo.notifications.Recipient;
import com.moga.khronosdemo.notifications.ShiftAssignedNotification;
import com.moga.khronosdemo.notifications.ShiftCancelledNotification;
import com.moga.khronosdemo.notifications.ShiftChangedNotification;
import com.moga.khronosdemo.notifications.ShiftDetails;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.services.WorkerShiftService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class WorkerShiftServiceImpl implements WorkerShiftService {

    private final WorkerShiftRepository workerShiftRepository;
    private final WorkerRepository workerRepository;
    private final ZoneRepository zoneRepository;
    private final WorkerShiftMapper workerShiftMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventAssignmentReconciler assignmentReconciler;

    public WorkerShiftServiceImpl(WorkerShiftRepository workerShiftRepository, WorkerRepository workerRepository,
                                  ZoneRepository zoneRepository, WorkerShiftMapper workerShiftMapper,
                                  ApplicationEventPublisher eventPublisher,
                                  EventAssignmentReconciler assignmentReconciler) {
        this.workerShiftRepository = workerShiftRepository;
        this.workerRepository = workerRepository;
        this.zoneRepository = zoneRepository;
        this.workerShiftMapper = workerShiftMapper;
        this.eventPublisher = eventPublisher;
        this.assignmentReconciler = assignmentReconciler;
    }

    @Override
    public List<WorkerShiftResponseDTO> create(List<WorkerShiftRequestDTO> requests) {

        List<WorkerShiftResponseDTO> created = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            WorkerShiftRequestDTO request = requests.get(i);

            Worker worker = getWorker(request.workerId());
            Zone zone = getZone(request.zoneId());

            try {
                validate(request, worker, zone, null);
            } catch (BusinessRuleException ex) {
                throw new BusinessRuleException("Shift [" + i + "]: " + ex.getMessage());
            }

            WorkerShift workerShift = workerShiftRepository.save(workerShiftMapper.toEntity(request, worker, zone));

            eventPublisher.publishEvent(new ShiftAssignedNotification(Recipient.of(worker), ShiftDetails.of(workerShift)));

            created.add(workerShiftMapper.toResponse(workerShift));
        }

        return created;
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

        Worker previousWorker = workerShift.getWorker();
        ShiftDetails before = ShiftDetails.of(workerShift);

        workerShiftMapper.updateEntity(workerShift, request, worker, zone);

        ShiftDetails after = ShiftDetails.of(workerShift);

        if (!previousWorker.getUserId().equals(worker.getUserId())) {
            eventPublisher.publishEvent(new ShiftCancelledNotification(Recipient.of(previousWorker), before));
            eventPublisher.publishEvent(new ShiftAssignedNotification(Recipient.of(worker), after));
            assignmentReconciler.unassignUncoveredEvents(previousWorker);
        } else if (!before.equals(after)) {
            eventPublisher.publishEvent(new ShiftChangedNotification(Recipient.of(worker), before, after));
            assignmentReconciler.unassignUncoveredEvents(worker);
        }

        return workerShiftMapper.toResponse(workerShift);

    }

    @Override
    public void delete(Long id) {
        WorkerShift workerShift = getWorkerShift(id);
        Worker worker = workerShift.getWorker();
        eventPublisher.publishEvent(new ShiftCancelledNotification(Recipient.of(worker), ShiftDetails.of(workerShift)));
        workerShiftRepository.delete(workerShift);
        assignmentReconciler.unassignUncoveredEvents(worker);
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

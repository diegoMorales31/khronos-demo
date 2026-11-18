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
import com.moga.khronosdemo.notifications.AssignmentBatch;
import com.moga.khronosdemo.notifications.AssignmentsNotification;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final TimeEntryShiftLinker timeEntryShiftLinker;

    public WorkerShiftServiceImpl(WorkerShiftRepository workerShiftRepository, WorkerRepository workerRepository,
                                  ZoneRepository zoneRepository, WorkerShiftMapper workerShiftMapper,
                                  ApplicationEventPublisher eventPublisher,
                                  EventAssignmentReconciler assignmentReconciler,
                                  TimeEntryShiftLinker timeEntryShiftLinker) {
        this.workerShiftRepository = workerShiftRepository;
        this.workerRepository = workerRepository;
        this.zoneRepository = zoneRepository;
        this.workerShiftMapper = workerShiftMapper;
        this.eventPublisher = eventPublisher;
        this.assignmentReconciler = assignmentReconciler;
        this.timeEntryShiftLinker = timeEntryShiftLinker;
    }

    @Override
    public List<WorkerShiftResponseDTO> create(List<WorkerShiftRequestDTO> requests) {
        AssignmentBatch notifications = new AssignmentBatch();
        List<WorkerShiftResponseDTO> created = create(requests, notifications);
        // One email per worker listing all their new shifts. Published inside the transaction, so if any
        // shift of the batch is rejected nothing is committed and no email is sent
        notifications.publish(eventPublisher);
        return created;
    }

    @Override
    public List<WorkerShiftResponseDTO> create(List<WorkerShiftRequestDTO> requests, AssignmentBatch notifications) {

        lockWorkers(requests.stream().map(WorkerShiftRequestDTO::workerId).toList());

        List<WorkerShiftResponseDTO> created = new ArrayList<>();
        List<WorkerShift> saved = new ArrayList<>();

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
            saved.add(workerShift);

            notifications.addShift(worker, ShiftDetails.of(workerShift));

            created.add(workerShiftMapper.toResponse(workerShift));
        }

        // Entries clocked in before their shift was entered are matched to it now, under the same locks
        timeEntryShiftLinker.linkToNewShifts(saved);

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
    public List<WorkerShiftResponseDTO> findByOwner(Long ownerId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return workerShiftRepository.findByOwner(ownerId, range.from(), range.to()).stream()
                .map(workerShiftMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findByWorker(Long workerId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return workerShiftRepository.findByWorker(workerId, range.from(), range.to()).stream()
                .map(workerShiftMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerShiftResponseDTO> findByZone(Long zoneId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return workerShiftRepository.findByZone(zoneId, range.from(), range.to()).stream()
                .map(workerShiftMapper::toResponse)
                .toList();
    }

    @Override
    public WorkerShiftResponseDTO update(Long id, WorkerShiftRequestDTO request) {

        WorkerShift workerShift = lockAndGet(id, request.workerId());
        Worker worker = getWorker(request.workerId());
        Zone zone = getZone(request.zoneId());

        validate(request, worker, zone, id);

        Worker previousWorker = workerShift.getWorker();
        ShiftDetails before = ShiftDetails.of(workerShift);

        workerShiftMapper.updateEntity(workerShift, request, worker, zone);
        timeEntryShiftLinker.relinkAfterShiftChange(workerShift);

        ShiftDetails after = ShiftDetails.of(workerShift);

        if (!previousWorker.getUserId().equals(worker.getUserId())) {
            eventPublisher.publishEvent(new ShiftCancelledNotification(Recipient.of(previousWorker), before));
            eventPublisher.publishEvent(new AssignmentsNotification(Recipient.of(worker), List.of(after), List.of()));
            assignmentReconciler.unassignUncoveredEvents(previousWorker, before, null);
        } else if (!before.equals(after)) {
            eventPublisher.publishEvent(new ShiftChangedNotification(Recipient.of(worker), before, after));
            assignmentReconciler.unassignUncoveredEvents(worker, before, after);
        }

        return workerShiftMapper.toResponse(workerShift);

    }

    // Time entries matched to the shift are kept; the database clears their link (shift_id)
    @Override
    public void delete(Long id) {
        WorkerShift workerShift = lockAndGet(id, null);
        Worker worker = workerShift.getWorker();
        ShiftDetails details = ShiftDetails.of(workerShift);
        eventPublisher.publishEvent(new ShiftCancelledNotification(Recipient.of(worker), details));
        workerShiftRepository.delete(workerShift);
        assignmentReconciler.unassignUncoveredEvents(worker, details, null);
    }

    private void validate(WorkerShiftRequestDTO request, Worker worker, Zone zone, Long shiftId) {

        if(worker.getActive().equals(false)) {
            throw new BusinessRuleException("Worker is not active");
        }

        // An end time before the start time means the shift ends the next day (18:00-02:00)
        if (request.endTime().equals(request.startTime())) {
            throw new BusinessRuleException("A shift cannot start and end at the same time");
        }

        if (WorkerShift.durationMinutes(request.startTime(), request.endTime()) > WorkerShift.MAX_DURATION_MINUTES) {
            throw new BusinessRuleException("A shift cannot last more than "
                    + WorkerShift.MAX_DURATION_MINUTES / 60 + " hours");
        }

        if (!zone.getEstablishment().getId().equals(worker.getEstablishment().getId())) {
            throw new BusinessRuleException("The zone does not belong to the worker's establishment");
        }

        LocalDateTime startsAt = request.date().atTime(request.startTime());
        LocalDateTime endsAt = WorkerShift.endOf(request.date(), request.startTime(), request.endTime());
        if (workerShiftRepository.existsOverlappingShift(worker.getUserId(), startsAt, endsAt, shiftId)) {
            String nextDay = WorkerShift.endsNextDay(request.startTime(), request.endTime()) ? " (+1)" : "";
            throw new BusinessRuleException("The worker already has a shift that overlaps with "
                    + request.startTime() + "-" + request.endTime() + nextDay + " on " + request.date());
        }

    }

    // The overlap and coverage checks read first and write later. Holding each worker's row lock
    // until commit makes concurrent requests for the same worker take turns, so a second request
    // sees the first one's shifts and assignments instead of both passing the checks.
    private void lockWorkers(List<Long> workerIds) {
        workerRepository.lockAllById(workerIds);
    }

    // Locks the shift's worker, and the worker it is being given to if any, in id order, and only then
    // loads the shift. A second change to the same shift waits for the first and starts from what it
    // committed, not from a copy read before the wait. If the shift was given to yet another worker
    // meanwhile, that worker's lock is not held and taking it now could deadlock, so the request is
    // refused and can be sent again.
    private WorkerShift lockAndGet(Long id, Long newWorkerId) {
        Long workerId = workerShiftRepository.findWorkerIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerShift", id));
        List<Long> lockedIds = newWorkerId != null ? List.of(workerId, newWorkerId) : List.of(workerId);
        lockWorkers(lockedIds);
        WorkerShift workerShift = getWorkerShift(id);
        if (!lockedIds.contains(workerShift.getWorker().getUserId())) {
            throw new BusinessRuleException("The shift was given to another worker while this request was"
                    + " waiting; reload it and try again");
        }
        return workerShift;
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

package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.WorkerEventMapper;
import com.moga.khronosdemo.notifications.AssignmentBatch;
import com.moga.khronosdemo.notifications.EventDetails;
import com.moga.khronosdemo.notifications.EventUnassignedNotification;
import com.moga.khronosdemo.notifications.Recipient;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.services.WorkerEventService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class WorkerEventServiceImpl implements WorkerEventService {

    private final WorkerEventRepository workerEventRepository;
    private final WorkerRepository workerRepository;
    private final EventRepository eventRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final WorkerEventMapper workerEventMapper;
    private final ApplicationEventPublisher eventPublisher;

    public WorkerEventServiceImpl(WorkerEventRepository workerEventRepository, WorkerRepository workerRepository,
                                  EventRepository eventRepository, WorkerShiftRepository workerShiftRepository,
                                  WorkerEventMapper workerEventMapper, ApplicationEventPublisher eventPublisher) {
        this.workerEventRepository = workerEventRepository;
        this.workerRepository = workerRepository;
        this.eventRepository = eventRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.workerEventMapper = workerEventMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<WorkerEventResponseDTO> create(List<WorkerEventRequestDTO> requests) {
        AssignmentBatch notifications = new AssignmentBatch();
        List<WorkerEventResponseDTO> created = create(requests, notifications);
        // One notification per worker listing all the events they were assigned to. Published inside the
        // transaction, so if any assignment of the batch is rejected nothing is committed and nobody is notified
        notifications.publish(eventPublisher);
        return created;
    }

    @Override
    public List<WorkerEventResponseDTO> create(List<WorkerEventRequestDTO> requests, AssignmentBatch notifications) {

        // Locks the events, then the workers, each in id order: the order every path that locks both
        // uses (see EventServiceImpl). A move or delete of one of the events, or a change to one of the
        // workers' shifts, waits until these assignments are committed, and the other way round, so
        // neither the event nor the covering shift can change while the coverage is being checked.
        eventRepository.lockAllById(requests.stream().map(WorkerEventRequestDTO::eventId).toList());
        workerRepository.lockAllById(requests.stream().map(WorkerEventRequestDTO::workerId).toList());

        List<WorkerEventResponseDTO> created = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            WorkerEventRequestDTO request = requests.get(i);

            Worker worker = workerRepository.findById(request.workerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Worker", request.workerId()));

            Event event = eventRepository.findById(request.eventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event", request.eventId()));

            try {
                validate(worker, event);
            } catch (BusinessRuleException ex) {
                throw new BusinessRuleException("Assignment [" + i + "]: " + ex.getMessage());
            }

            WorkerEvent workerEvent = workerEventRepository.save(workerEventMapper.toEntity(request, worker, event));

            notifications.addEvent(worker, EventDetails.of(event));

            created.add(workerEventMapper.toResponse(workerEvent));
        }

        return created;
    }

    private void validate(Worker worker, Event event) {

        if (worker.getActive().equals(false)) {
            throw new BusinessRuleException("Worker is not active");
        }

        if (workerEventRepository.existsByWorker_UserIdAndEvent_Id(worker.getUserId(), event.getId())) {
            throw new BusinessRuleException("Worker is already assigned to this event");
        }

        if (!event.getEstablishment().getId().equals(worker.getEstablishment().getId())) {
            throw new BusinessRuleException("The event does not belong to the worker's establishment");
        }

        LocalDate eventDate = event.getDateTime().toLocalDate();
        LocalTime eventTime = event.getDateTime().toLocalTime();

        // A shift from the day before that runs past midnight can cover an event in the early hours
        if (!workerShiftRepository.existsShiftCoveringEvent(worker.getUserId(), event.getZone().getId(), event.getDateTime())) {
            throw new BusinessRuleException("The worker has no shift in zone '" + event.getZone().getName()
                    + "' on " + eventDate + " that has started by " + eventTime + " and is still running");
        }

    }

    @Override
    @Transactional(readOnly = true)
    public WorkerEventResponseDTO findById(Long id) {
        return workerEventMapper.toResponse(getWorkerEvent(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerEventResponseDTO> findByWorker(Long workerId) {
        return workerEventRepository.findByWorker_UserId(workerId).stream().map(workerEventMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerEventResponseDTO> findByEvent(Long eventId) {
        return workerEventRepository.findByEvent_Id(eventId).stream().map(workerEventMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerEventResponseDTO> findByEstablishment(Long establishmentId, Long workerId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return workerEventRepository.findByEstablishment(establishmentId, workerId, range.startInclusive(), range.endExclusive())
                .stream()
                .map(workerEventMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(Long id) {
        WorkerEvent workerEvent = getWorkerEvent(id);
        eventPublisher.publishEvent(new EventUnassignedNotification(
                Recipient.of(workerEvent.getWorker()), EventDetails.of(workerEvent.getEvent()), null));
        workerEventRepository.delete(workerEvent);
    }

    private WorkerEvent getWorkerEvent(Long id) {
        return workerEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerEvent", id));
    }
}

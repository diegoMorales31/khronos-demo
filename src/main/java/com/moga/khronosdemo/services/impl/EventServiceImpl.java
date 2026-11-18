package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.EventMapper;
import com.moga.khronosdemo.notifications.EventDetails;
import com.moga.khronosdemo.notifications.EventUnassignedNotification;
import com.moga.khronosdemo.notifications.Recipient;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.services.EventService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final WorkerRepository workerRepository;
    private final WorkerEventRepository workerEventRepository;
    private final EventMapper eventMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventAssignmentReconciler assignmentReconciler;

    public EventServiceImpl(EventRepository eventRepository, ZoneRepository zoneRepository,
                            WorkerRepository workerRepository, WorkerEventRepository workerEventRepository,
                            EventMapper eventMapper, ApplicationEventPublisher eventPublisher,
                            EventAssignmentReconciler assignmentReconciler) {
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.workerRepository = workerRepository;
        this.workerEventRepository = workerEventRepository;
        this.eventMapper = eventMapper;
        this.eventPublisher = eventPublisher;
        this.assignmentReconciler = assignmentReconciler;
    }

    @Override
    public List<EventResponseDTO> create(List<EventRequestDTO> requests) {

        List<EventResponseDTO> created = new ArrayList<>();

        for (EventRequestDTO request : requests) {
            Event event = eventMapper.toEntity(request, getZone(request.zoneId()));
            created.add(eventMapper.toResponse(eventRepository.save(event)));
        }

        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponseDTO findById(Long id) {
        return eventMapper.toResponse(getEvent(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponseDTO> findAll() {
        return eventRepository.findAll().stream().map(eventMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponseDTO> findByZone(Long zoneId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return eventRepository.findByZone(zoneId, range.startInclusive(), range.endExclusive()).stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = new DateRange(from, to);
        return eventRepository.findByEstablishment(establishmentId, range.startInclusive(), range.endExclusive()).stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    @Override
    public EventResponseDTO update(Long id, EventRequestDTO request) {

        Event event = lockWithAssignedWorkers(id);
        Zone zone = getZone(request.zoneId());

        boolean changesEstablishment = !zone.getEstablishment()
                .getId()
                .equals(event.getEstablishment().getId());

        if (changesEstablishment && !event.getWorkerEvents().isEmpty()) {
            throw new BusinessRuleException("Cannot move an event with assigned workers to another establishment");
        }

        EventDetails before = EventDetails.of(event);

        eventMapper.updateEntity(event, request, zone);

        assignmentReconciler.reconcileEventChange(event, before);

        return eventMapper.toResponse(event);

    }

    @Override
    public void delete(Long id) {
        Event event = lockWithAssignedWorkers(id);
        EventDetails details = EventDetails.of(event);
        event.getWorkerEvents().forEach(workerEvent -> eventPublisher.publishEvent(
                new EventUnassignedNotification(Recipient.of(workerEvent.getWorker()), details,
                        EventUnassignedNotification.EVENT_CANCELLED)));
        eventRepository.delete(event);
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }

    // Moving or deleting an event checks its assignments against the workers' shifts, so it takes the
    // event's lock and then its workers' locks, in the order every path uses (events by id, then workers
    // by id). An assignment being made to the event, or a shift change of one of its workers, waits until
    // this change commits and then sees it, instead of acting on the event's old time or zone. Assignments
    // are only added under the event's lock, so none can appear after the workers are read here.
    private Event lockWithAssignedWorkers(Long id) {
        Event event = eventRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
        List<Long> workerIds = workerEventRepository.findWorkerIdsByEventId(id);
        if (!workerIds.isEmpty()) {
            workerRepository.lockAllById(workerIds);
        }
        return event;
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }
}

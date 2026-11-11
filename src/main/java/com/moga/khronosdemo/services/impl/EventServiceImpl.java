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
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.services.EventService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final EventMapper eventMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EventAssignmentReconciler assignmentReconciler;

    public EventServiceImpl(EventRepository eventRepository, ZoneRepository zoneRepository, EventMapper eventMapper,
                            ApplicationEventPublisher eventPublisher, EventAssignmentReconciler assignmentReconciler) {
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.eventMapper = eventMapper;
        this.eventPublisher = eventPublisher;
        this.assignmentReconciler = assignmentReconciler;
    }

    @Override
    public EventResponseDTO create(EventRequestDTO request) {
        Event event = eventMapper.toEntity(request, getZone(request.zoneId()));
        return eventMapper.toResponse(eventRepository.save(event));
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
    public List<EventResponseDTO> findByZone(Long zoneId) {
        return eventRepository.findByZone_Id(zoneId).stream().map(eventMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponseDTO> findByEstablishment(Long establishmentId) {
        return eventRepository.findByZone_Establishment_Id(establishmentId).stream().map(eventMapper::toResponse).toList();
    }

    @Override
    public EventResponseDTO update(Long id, EventRequestDTO request) {

        Event event = getEvent(id);
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
        Event event = getEvent(id);
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

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }
}

package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ShiftsWithEventsRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.ShiftsWithEventsResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.notifications.AssignmentBatch;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.services.ScheduleService;
import com.moga.khronosdemo.services.WorkerEventService;
import com.moga.khronosdemo.services.WorkerShiftService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ScheduleServiceImpl implements ScheduleService {

    private final WorkerShiftService workerShiftService;
    private final WorkerEventService workerEventService;
    private final EventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ScheduleServiceImpl(WorkerShiftService workerShiftService, WorkerEventService workerEventService,
                               EventRepository eventRepository, ApplicationEventPublisher eventPublisher) {
        this.workerShiftService = workerShiftService;
        this.workerEventService = workerEventService;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ShiftsWithEventsResponseDTO createShiftsWithEvents(ShiftsWithEventsRequestDTO request) {
        // Every path that locks both takes the events first and the workers after (see EventRepository).
        // Creating the shifts locks the workers, so the events are locked here, before that
        eventRepository.lockAllById(request.assignments().stream().map(WorkerEventRequestDTO::eventId).toList());

        AssignmentBatch notifications = new AssignmentBatch();
        List<WorkerShiftResponseDTO> shifts = workerShiftService.create(request.shifts(), notifications);
        // The assignments are checked against the shifts just saved, in the same transaction
        List<WorkerEventResponseDTO> assignments = request.assignments().isEmpty()
                ? List.of()
                : workerEventService.create(request.assignments(), notifications);

        // One email per worker with their new shifts and events together, sent only after commit
        notifications.publish(eventPublisher);
        return new ShiftsWithEventsResponseDTO(shifts, assignments);
    }
}

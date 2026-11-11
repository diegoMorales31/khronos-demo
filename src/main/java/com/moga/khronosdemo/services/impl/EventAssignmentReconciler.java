package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.notifications.EventChangedNotification;
import com.moga.khronosdemo.notifications.EventDetails;
import com.moga.khronosdemo.notifications.EventUnassignedNotification;
import com.moga.khronosdemo.notifications.Recipient;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventAssignmentReconciler {

    private final WorkerEventRepository workerEventRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EventAssignmentReconciler(WorkerEventRepository workerEventRepository,
                                     WorkerShiftRepository workerShiftRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.workerEventRepository = workerEventRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.eventPublisher = eventPublisher;
    }

    public void unassignUncoveredEvents(Worker worker) {
        workerShiftRepository.flush();
        for (WorkerEvent workerEvent : workerEventRepository.findByWorker_UserId(worker.getUserId())) {
            Event event = workerEvent.getEvent();
            if (!isCovered(worker, event)) {
                unassign(workerEvent, EventDetails.of(event));
            }
        }
    }

    public void reconcileEventChange(Event event, EventDetails before) {
        EventDetails after = EventDetails.of(event);
        for (WorkerEvent workerEvent : List.copyOf(event.getWorkerEvents())) {
            Worker worker = workerEvent.getWorker();
            if (!isCovered(worker, event)) {
                unassign(workerEvent, before);
            } else if (!before.equals(after)) {
                eventPublisher.publishEvent(new EventChangedNotification(Recipient.of(worker), before, after));
            }
        }
    }

    private boolean isCovered(Worker worker, Event event) {
        return workerShiftRepository.existsShiftCoveringEvent(
                worker.getUserId(),
                event.getZone().getId(),
                event.getDateTime().toLocalDate(),
                event.getDateTime().toLocalTime());
    }

    private void unassign(WorkerEvent workerEvent, EventDetails details) {
        workerEvent.getEvent().getWorkerEvents().remove(workerEvent);
        workerEvent.getWorker().getWorkerEvents().remove(workerEvent);
        workerEventRepository.delete(workerEvent);
        eventPublisher.publishEvent(new EventUnassignedNotification(
                Recipient.of(workerEvent.getWorker()), details, EventUnassignedNotification.NO_COVERING_SHIFT));
    }
}

package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.notifications.EventChangedNotification;
import com.moga.khronosdemo.notifications.EventDetails;
import com.moga.khronosdemo.notifications.EventUnassignedNotification;
import com.moga.khronosdemo.notifications.Recipient;
import com.moga.khronosdemo.notifications.ShiftDetails;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import org.hibernate.Hibernate;
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

    /**
     * After one of the worker's shifts was edited, deleted or given to another worker, unassigns the worker
     * from the events their shifts no longer cover. Only an event inside the shift's interval before or
     * after the change can have lost its cover, so only those are checked, in one query, however many
     * assignments the worker has. {@code after} is null when the worker no longer has the shift.
     */
    public void unassignUncoveredEvents(Worker worker, ShiftDetails before, ShiftDetails after) {
        workerShiftRepository.flush();
        ShiftDetails other = after != null ? after : before;
        List<WorkerEvent> uncovered = workerEventRepository.findUncoveredByWorkerBetween(worker.getUserId(),
                before.startDateTime(), before.endDateTime(), other.startDateTime(), other.endDateTime());
        for (WorkerEvent workerEvent : uncovered) {
            unassign(workerEvent, EventDetails.of(workerEvent.getEvent()));
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
                event.getDateTime());
    }

    private void unassign(WorkerEvent workerEvent, EventDetails details) {
        removeIfLoaded(workerEvent.getEvent().getWorkerEvents(), workerEvent);
        removeIfLoaded(workerEvent.getWorker().getWorkerEvents(), workerEvent);
        workerEventRepository.delete(workerEvent);
        eventPublisher.publishEvent(new EventUnassignedNotification(
                Recipient.of(workerEvent.getWorker()), details, EventUnassignedNotification.NO_COVERING_SHIFT));
    }

    // A collection that is already loaded must not keep the deleted assignment, or the cascade would save
    // it again. One that is not loaded is left alone: loading it would read every assignment of the worker
    // or event just to remove one, and none of the requests that reconcile reads it afterwards.
    private static void removeIfLoaded(List<WorkerEvent> assignments, WorkerEvent workerEvent) {
        if (Hibernate.isInitialized(assignments)) {
            assignments.remove(workerEvent);
        }
    }
}

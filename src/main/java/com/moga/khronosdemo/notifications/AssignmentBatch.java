package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.Worker;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the shifts and events assigned during one request, per worker, and publishes one
 * AssignmentsNotification per worker at the end. Publish it inside the transaction: the email is only
 * sent after commit, so a request that fails anywhere sends nothing.
 */
public class AssignmentBatch {

    private final Map<Long, Recipient> recipients = new LinkedHashMap<>();
    private final Map<Long, List<ShiftDetails>> shifts = new LinkedHashMap<>();
    private final Map<Long, List<EventDetails>> events = new LinkedHashMap<>();

    public void addShift(Worker worker, ShiftDetails shift) {
        recipients.putIfAbsent(worker.getUserId(), Recipient.of(worker));
        shifts.computeIfAbsent(worker.getUserId(), id -> new ArrayList<>()).add(shift);
    }

    public void addEvent(Worker worker, EventDetails event) {
        recipients.putIfAbsent(worker.getUserId(), Recipient.of(worker));
        events.computeIfAbsent(worker.getUserId(), id -> new ArrayList<>()).add(event);
    }

    public void publish(ApplicationEventPublisher publisher) {
        recipients.forEach((workerId, recipient) -> publisher.publishEvent(new AssignmentsNotification(recipient,
                shifts.getOrDefault(workerId, List.of()), events.getOrDefault(workerId, List.of()))));
    }
}

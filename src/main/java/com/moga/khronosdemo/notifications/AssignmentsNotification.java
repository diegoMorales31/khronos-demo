package com.moga.khronosdemo.notifications;

import java.util.List;

// Everything assigned to one worker in the same request, shifts and events together, so they get a single notification
public record AssignmentsNotification(Recipient recipient, List<ShiftDetails> shifts, List<EventDetails> events) {

    public AssignmentsNotification {
        // The push is built on another thread after commit; keep copies the caller can't change
        shifts = List.copyOf(shifts);
        events = List.copyOf(events);
    }
}

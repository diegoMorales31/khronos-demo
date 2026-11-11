package com.moga.khronosdemo.notifications;

public record EventUnassignedNotification(Recipient recipient, EventDetails event, String reason) {

    public static final String EVENT_CANCELLED = "The event has been cancelled.";
    public static final String NO_COVERING_SHIFT = "You no longer have a shift that covers this event.";
}

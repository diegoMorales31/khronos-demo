package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.Event;

import java.time.LocalDateTime;

public record EventDetails(String title, LocalDateTime dateTime, String zoneName, String description) {

    public static EventDetails of(Event event) {
        return new EventDetails(event.getTitle(), event.getDateTime(), event.getZone().getName(), event.getDescription());
    }
}

package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Zone;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventResponseDTO toResponse(Event event) {
        if (event == null) {
            return null;
        }
        Zone zone = event.getZone();
        return new EventResponseDTO(
                event.getId(),
                event.getTitle(),
                event.getDateTime(),
                zone != null ? zone.getId() : null,
                zone != null ? zone.getName() : null,
                event.getPeopleNumber(),
                event.getEstablishment() != null ? event.getEstablishment().getId() : null,
                event.getDescription()
        );
    }

    public Event toEntity(EventRequestDTO request, Zone zone) {
        if (request == null) {
            return null;
        }
        Event event = new Event();
        updateEntity(event, request, zone);
        return event;
    }

    public void updateEntity(Event event, EventRequestDTO request, Zone zone) {
        event.setTitle(request.title());
        event.setDateTime(request.dateTime());
        event.setZone(zone);
        event.setPeopleNumber(request.peopleNumber());
        event.setDescription(request.description());
    }
}

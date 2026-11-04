package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventResponseDTO toResponse(Event event) {
        if (event == null) {
            return null;
        }
        return new EventResponseDTO(
                event.getId(),
                event.getDateTime(),
                event.getWorkZone(),
                event.getPeopleNumber(),
                event.getEstablishment() != null ? event.getEstablishment().getId() : null,
                event.getObservation()
        );
    }

    public Event toEntity(EventRequestDTO request, Establishment establishment) {
        if (request == null) {
            return null;
        }
        Event event = new Event();
        updateEntity(event, request, establishment);
        return event;
    }

    public void updateEntity(Event event, EventRequestDTO request, Establishment establishment) {
        event.setDateTime(request.dateTime());
        event.setWorkZone(request.workZone());
        event.setPeopleNumber(request.peopleNumber());
        event.setEstablishment(establishment);
        event.setObservation(request.observation());
    }
}

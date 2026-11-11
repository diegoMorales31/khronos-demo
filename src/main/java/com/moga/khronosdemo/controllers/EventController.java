package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.response.EventResponseDTO;
import com.moga.khronosdemo.services.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsZones(#request.![zoneId()])")
    public List<EventResponseDTO> create(@RequestBody @NotEmpty List<@Valid EventRequestDTO> request) {
        return eventService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<EventResponseDTO> findAll() {
        return eventService.findAll();
    }

    @GetMapping("/zone/{zoneId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfZone(#zoneId)")
    public List<EventResponseDTO> findByZone(@PathVariable Long zoneId) {
        return eventService.findByZone(zoneId);
    }

    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEstablishment(#establishmentId)")
    public List<EventResponseDTO> findByEstablishment(@PathVariable Long establishmentId) {
        return eventService.findByEstablishment(establishmentId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEvent(#id)")
    public EventResponseDTO findById(@PathVariable Long id) {
        return eventService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsEvent(#id) and @access.ownsZone(#request.zoneId()))")
    public EventResponseDTO update(@PathVariable Long id, @Valid @RequestBody EventRequestDTO request) {
        return eventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEvent(#id)")
    public void delete(@PathVariable Long id) {
        eventService.delete(id);
    }
}

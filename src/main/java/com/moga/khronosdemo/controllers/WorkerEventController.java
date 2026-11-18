package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.WorkerEventService;
import com.moga.khronosdemo.validation.MaxBatchSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/worker-events")
public class WorkerEventController {

    private final WorkerEventService workerEventService;

    public WorkerEventController(WorkerEventService workerEventService) {
        this.workerEventService = workerEventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsWorkers(#request.![workerId()]) and @access.ownsEvents(#request.![eventId()]))")
    public List<WorkerEventResponseDTO> create(@RequestBody @NotEmpty @MaxBatchSize List<@Valid WorkerEventRequestDTO> request) {
        return workerEventService.create(request);
    }

    @GetMapping("/worker/{workerId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#workerId) or @access.ownsWorker(#workerId)")
    public List<WorkerEventResponseDTO> findByWorker(@PathVariable Long workerId) {
        return workerEventService.findByWorker(workerId);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEvent(#eventId)")
    public List<WorkerEventResponseDTO> findByEvent(@PathVariable Long eventId) {
        return workerEventService.findByEvent(eventId);
    }

    // Every assignment of the establishment's events in one request, instead of one per event.
    // A worker only gets their own, not the rest of the staff's.
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEstablishment(#establishmentId)")
    public List<WorkerEventResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @AuthenticationPrincipal SecurityUser principal) {
        Long workerId = principal.getRole() == Role.WORKER ? principal.getId() : null;
        return workerEventService.findByEstablishment(establishmentId, workerId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isOwnWorkerEvent(#id) or @access.ownsWorkerEvent(#id)")
    public WorkerEventResponseDTO findById(@PathVariable Long id) {
        return workerEventService.findById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorkerEvent(#id)")
    public void delete(@PathVariable Long id) {
        workerEventService.delete(id);
    }
}

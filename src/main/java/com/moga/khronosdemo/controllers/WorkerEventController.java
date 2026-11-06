package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerEventResponseDTO;
import com.moga.khronosdemo.services.WorkerEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsWorker(#request.workerId()) and @access.ownsEvent(#request.eventId()))")
    public WorkerEventResponseDTO create(@Valid @RequestBody WorkerEventRequestDTO request) {
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

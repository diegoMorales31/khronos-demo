package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.services.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public WorkerResponseDTO create(@Valid @RequestBody WorkerRequestDTO request) {
        return workerService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<WorkerResponseDTO> findAll() {
        return workerService.findAll();
    }

    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<WorkerResponseDTO> findByEstablishment(@PathVariable Long establishmentId) {
        return workerService.findByEstablishment(establishmentId);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#userId) or @access.ownsWorker(#userId)")
    public WorkerResponseDTO findById(@PathVariable Long userId) {
        return workerService.findById(userId);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsWorker(#userId) and @access.ownsEstablishment(#request.establishmentId()))")
    public WorkerResponseDTO update(@PathVariable Long userId, @Valid @RequestBody WorkerRequestDTO request) {
        return workerService.update(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#userId)")
    public void delete(@PathVariable Long userId) {
        workerService.delete(userId);
    }
}

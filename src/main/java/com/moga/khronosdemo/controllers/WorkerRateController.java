package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerRateRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerRateResponseDTO;
import com.moga.khronosdemo.services.WorkerRateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Pay rates are only for the worker's owner and admins; a worker never sees rates, not even their own
@RestController
@RequestMapping("/api/workers/{workerId}/rates")
public class WorkerRateController {

    private final WorkerRateService workerRateService;

    public WorkerRateController(WorkerRateService workerRateService) {
        this.workerRateService = workerRateService;
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#workerId)")
    public List<WorkerRateResponseDTO> findByWorker(@PathVariable Long workerId) {
        return workerRateService.findByWorker(workerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#workerId)")
    public WorkerRateResponseDTO create(@PathVariable Long workerId, @Valid @RequestBody WorkerRateRequestDTO request) {
        return workerRateService.create(workerId, request);
    }

    @DeleteMapping("/{rateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#workerId)")
    public void delete(@PathVariable Long workerId, @PathVariable Long rateId) {
        workerRateService.delete(workerId, rateId);
    }
}

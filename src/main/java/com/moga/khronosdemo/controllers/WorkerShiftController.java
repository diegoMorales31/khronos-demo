package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerShiftResponseDTO;
import com.moga.khronosdemo.services.WorkerShiftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/worker-shifts")
public class WorkerShiftController {

    private final WorkerShiftService workerShiftService;

    public WorkerShiftController(WorkerShiftService workerShiftService) {
        this.workerShiftService = workerShiftService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorkers(#request.![workerId()])")
    public List<WorkerShiftResponseDTO> create(@RequestBody @NotEmpty List<@Valid WorkerShiftRequestDTO> request) {
        return workerShiftService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<WorkerShiftResponseDTO> findAll() {
        return workerShiftService.findAll();
    }

    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('OWNER') and @access.isSelf(#ownerId))")
    public List<WorkerShiftResponseDTO> findByOwner(@PathVariable Long ownerId) {
        return workerShiftService.findByOwner(ownerId);
    }

    @GetMapping("/worker/{workerId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#workerId) or @access.ownsWorker(#workerId)")
    public List<WorkerShiftResponseDTO> findByWorker(@PathVariable Long workerId) {
        return workerShiftService.findByWorker(workerId);
    }

    @GetMapping("/zone/{zoneId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsZone(#zoneId)")
    public List<WorkerShiftResponseDTO> findByZone(@PathVariable Long zoneId) {
        return workerShiftService.findByZone(zoneId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isOwnShift(#id) or @access.ownsShift(#id)")
    public WorkerShiftResponseDTO findById(@PathVariable Long id) {
        return workerShiftService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsShift(#id) and @access.ownsWorker(#request.workerId()))")
    public WorkerShiftResponseDTO update(@PathVariable Long id, @Valid @RequestBody WorkerShiftRequestDTO request) {
        return workerShiftService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsShift(#id)")
    public void delete(@PathVariable Long id) {
        workerShiftService.delete(id);
    }
}

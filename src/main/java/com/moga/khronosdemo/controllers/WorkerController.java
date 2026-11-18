package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.WorkerActiveRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerWithUserRequestDTO;
import com.moga.khronosdemo.dtos.response.WorkerResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerWithUserResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsEstablishment(#request.establishmentId()) and @access.createdUser(#request.userId()))")
    public WorkerResponseDTO create(@Valid @RequestBody WorkerRequestDTO request) {
        return workerService.create(request);
    }

    // Creates the WORKER account and links it to the establishment in one transaction
    @PostMapping("/with-user")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public WorkerWithUserResponseDTO createWithUser(@Valid @RequestBody WorkerWithUserRequestDTO request,
                                                    @AuthenticationPrincipal SecurityUser principal) {
        return workerService.createWithUser(request, principal.getId());
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
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void delete(@PathVariable Long userId) {
        workerService.delete(userId);
    }

    // Sets the flag to the given value, so repeating the request is safe
    @PutMapping("/{userId}/active")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#userId)")
    public WorkerResponseDTO setActive(@PathVariable Long userId, @Valid @RequestBody WorkerActiveRequestDTO request) {
        return workerService.setActive(userId, request.active());
    }

    /**
     * @deprecated toggles the flag, so a retried request undoes itself. Use PUT /api/workers/{userId}/active.
     */
    @Deprecated
    @DeleteMapping("/deactivate/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#userId)")
    public void deactivate(@PathVariable Long userId) {workerService.changeActivationStatus(userId);}

}

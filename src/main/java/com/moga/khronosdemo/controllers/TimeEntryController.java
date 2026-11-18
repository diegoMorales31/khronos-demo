package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.ClockInRequestDTO;
import com.moga.khronosdemo.dtos.request.ClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryCorrectionRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryVoidRequestDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.TimeEntryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Time tracking. Workers clock themselves in and out with the server's time; the establishment's owner
 * or an admin can enter, clock out, correct and void entries, always with a reason that is kept in the history.
 */
@RestController
@RequestMapping("/api/time-entries")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;

    public TimeEntryController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    // 204 when the worker is not clocked in
    @GetMapping("/me/open")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<TimeEntryResponseDTO> findMyOpenEntry(@AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.findOpenOfWorker(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/clock-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WORKER')")
    public TimeEntryResponseDTO clockIn(@Valid @RequestBody(required = false) ClockInRequestDTO request,
                                        @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.clockIn(principal.getId(), request);
    }

    @PostMapping("/clock-out")
    @PreAuthorize("hasRole('WORKER')")
    public TimeEntryResponseDTO clockOut(@Valid @RequestBody(required = false) ClockOutRequestDTO request,
                                         @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.clockOut(principal.getId(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsWorker(#request.workerId())")
    public TimeEntryResponseDTO create(@Valid @RequestBody TimeEntryRequestDTO request,
                                       @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.create(request, principal.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsTimeEntry(#id)")
    public TimeEntryResponseDTO correct(@PathVariable Long id, @Valid @RequestBody TimeEntryCorrectionRequestDTO request,
                                        @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.correct(id, request, principal.getId());
    }

    // Clocks out a worker who left without doing it, with the server's time
    @PostMapping("/{id}/clock-out")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsTimeEntry(#id)")
    public TimeEntryResponseDTO clockOutEntry(@PathVariable Long id,
                                              @Valid @RequestBody TimeEntryClockOutRequestDTO request,
                                              @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.clockOutEntry(id, request, principal.getId());
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsTimeEntry(#id)")
    public TimeEntryResponseDTO voidEntry(@PathVariable Long id, @Valid @RequestBody TimeEntryVoidRequestDTO request,
                                          @AuthenticationPrincipal SecurityUser principal) {
        return timeEntryService.voidEntry(id, request.reason(), principal.getId());
    }

    // Oldest change first
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsTimeEntry(#id) or @access.isOwnTimeEntry(#id)")
    public List<TimeEntryChangeResponseDTO> findHistory(@PathVariable Long id) {
        return timeEntryService.findHistory(id);
    }

    // from and to filter the business date; voided entries are left out unless includeVoided=true
    @GetMapping("/worker/{workerId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#workerId) or @access.ownsWorker(#workerId)")
    public List<TimeEntryResponseDTO> findByWorker(@PathVariable Long workerId,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "false") boolean includeVoided) {
        return timeEntryService.findByWorker(workerId, from, to, includeVoided);
    }

    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<TimeEntryResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                          @RequestParam(defaultValue = "false") boolean includeVoided) {
        return timeEntryService.findByEstablishment(establishmentId, from, to, includeVoided);
    }

    // Who is clocked in right now
    @GetMapping("/establishment/{establishmentId}/open")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<TimeEntryResponseDTO> findOpenByEstablishment(@PathVariable Long establishmentId) {
        return timeEntryService.findOpenByEstablishment(establishmentId);
    }
}

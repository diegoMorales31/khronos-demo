package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.CashClosingCloseRequestDTO;
import com.moga.khronosdemo.dtos.request.CashClosingReopenRequestDTO;
import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.CashClosingService;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The daily cash closing of an establishment: one per business day, entered by hand, closed and
 * reopened with every change kept in its history. Only the establishment's owner or an admin can read
 * or change closings; workers never see them.
 */
@RestController
@RequestMapping("/api/cash-closings")
public class CashClosingController {

    private final CashClosingService cashClosingService;

    public CashClosingController(CashClosingService cashClosingService) {
        this.cashClosingService = cashClosingService;
    }

    // Latest business day first; from and to are optional and include both ends
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<CashClosingResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return cashClosingService.findByEstablishment(establishmentId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public CashClosingResponseDTO findById(@PathVariable Long id) {
        return cashClosingService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public CashClosingResponseDTO create(@Validated(OnCreate.class) @RequestBody CashClosingRequestDTO request,
                                         @AuthenticationPrincipal SecurityUser principal) {
        return cashClosingService.create(request, principal.getId());
    }

    // establishmentId and businessDate in the body are ignored: a closing stays on its day
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public CashClosingResponseDTO update(@PathVariable Long id, @Valid @RequestBody CashClosingRequestDTO request,
                                         @AuthenticationPrincipal SecurityUser principal) {
        return cashClosingService.update(id, request, principal.getId());
    }

    // The body is optional; differenceReason is only required when the difference is over the threshold
    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public CashClosingResponseDTO close(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) CashClosingCloseRequestDTO request,
                                        @AuthenticationPrincipal SecurityUser principal) {
        return cashClosingService.close(id, request != null ? request.differenceReason() : null, principal.getId());
    }

    @PutMapping("/{id}/reopen")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public CashClosingResponseDTO reopen(@PathVariable Long id, @Valid @RequestBody CashClosingReopenRequestDTO request,
                                         @AuthenticationPrincipal SecurityUser principal) {
        return cashClosingService.reopen(id, request.reason(), principal.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public void delete(@PathVariable Long id) {
        cashClosingService.delete(id);
    }

    // Oldest change first
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsCashClosing(#id)")
    public List<CashClosingChangeResponseDTO> findHistory(@PathVariable Long id) {
        return cashClosingService.findHistory(id);
    }
}

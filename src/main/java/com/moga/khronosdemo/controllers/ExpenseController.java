package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PlanFeature;
import com.moga.khronosdemo.security.RequiresPlan;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.ExpenseService;
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
 * An establishment's expenses, entered by hand. Only the establishment's owner or an admin can read or
 * change them; workers never see them.
 */
@RestController
@RequiresPlan(PlanFeature.CASH)
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // from and to filter the business day and include both ends; every filter is optional
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<ExpenseResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) ExpenseCategory category) {
        return expenseService.findByEstablishment(establishmentId, from, to, category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public ExpenseResponseDTO create(@Validated(OnCreate.class) @RequestBody ExpenseRequestDTO request,
                                     @AuthenticationPrincipal SecurityUser principal) {
        return expenseService.create(request, principal.getId());
    }

    // establishmentId in the body is ignored: an expense stays where it was created
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsExpense(#id)")
    public ExpenseResponseDTO update(@PathVariable Long id, @Valid @RequestBody ExpenseRequestDTO request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsExpense(#id)")
    public void delete(@PathVariable Long id) {
        expenseService.delete(id);
    }
}

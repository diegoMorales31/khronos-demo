package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.StockMovementRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.enums.PlanFeature;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.security.RequiresPlan;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.StockMovementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * The stock ledger: purchases, waste, consumption and adjustments entered by hand. Movements are only
 * ever added, so there is no PUT or DELETE: a mistake is corrected with another movement. Only the
 * establishment's owner or an admin can read or record them.
 */
@RestController
@RequiresPlan(PlanFeature.INVENTORY)
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockMovementService stockMovementService;

    public StockMovementController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    // from and to filter the business day and include both ends; every filter is optional
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<StockMovementResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                              @RequestParam(required = false) Long productId,
                                                              @RequestParam(required = false) StockMovementType type) {
        return stockMovementService.findByEstablishment(establishmentId, from, to, productId, type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsProduct(#request.productId())")
    public StockMovementResponseDTO create(@Valid @RequestBody StockMovementRequestDTO request,
                                           @AuthenticationPrincipal SecurityUser principal) {
        return stockMovementService.create(request, principal.getId());
    }
}

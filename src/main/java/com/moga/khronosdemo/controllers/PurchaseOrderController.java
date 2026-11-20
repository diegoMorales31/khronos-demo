package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderRequestDTO;
import com.moga.khronosdemo.dtos.response.PurchaseOrderResponseDTO;
import com.moga.khronosdemo.entities.enums.PlanFeature;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import com.moga.khronosdemo.security.RequiresPlan;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.PurchaseOrderService;
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
 * Purchase orders to suppliers, from draft to received. Only the establishment's owner or an admin can
 * read or change them, and the supplier and products named in a body must be theirs too.
 */
@RestController
@RequiresPlan(PlanFeature.INVENTORY)
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    // Newest first. from and to filter the day it was created, in the establishment's time zone, and
    // include both ends; every filter is optional
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<PurchaseOrderResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                              @RequestParam(required = false) PurchaseOrderStatus status,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return purchaseOrderService.findByEstablishment(establishmentId, status, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsPurchaseOrder(#id)")
    public PurchaseOrderResponseDTO findById(@PathVariable Long id) {
        return purchaseOrderService.findById(id);
    }

    // A new DRAFT
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsEstablishment(#request.establishmentId())"
            + " and @access.ownsSupplier(#request.supplierId()) and @access.ownsProducts(#request.lines().![productId()]))")
    public PurchaseOrderResponseDTO create(@Validated(OnCreate.class) @RequestBody PurchaseOrderRequestDTO request,
                                           @AuthenticationPrincipal SecurityUser principal) {
        return purchaseOrderService.create(request, principal.getId());
    }

    // Only a DRAFT. establishmentId in the body is ignored: an order stays where it was created
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsPurchaseOrder(#id)"
            + " and @access.ownsSupplier(#request.supplierId()) and @access.ownsProducts(#request.lines().![productId()]))")
    public PurchaseOrderResponseDTO update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequestDTO request) {
        return purchaseOrderService.update(id, request);
    }

    // Only a DRAFT
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsPurchaseOrder(#id)")
    public void delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
    }

    // DRAFT -> ORDERED, ordered today
    @PutMapping("/{id}/order")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsPurchaseOrder(#id)")
    public PurchaseOrderResponseDTO order(@PathVariable Long id) {
        return purchaseOrderService.order(id);
    }

    // DRAFT or ORDERED -> CANCELLED
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsPurchaseOrder(#id)")
    public PurchaseOrderResponseDTO cancel(@PathVariable Long id) {
        return purchaseOrderService.cancel(id);
    }

    // ORDERED -> RECEIVED: records the stock that came in and, if asked, the expense
    @PutMapping("/{id}/receive")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsPurchaseOrder(#id)")
    public PurchaseOrderResponseDTO receive(@PathVariable Long id,
                                            @Valid @RequestBody PurchaseOrderReceiveRequestDTO request,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return purchaseOrderService.receive(id, request, principal.getId());
    }
}

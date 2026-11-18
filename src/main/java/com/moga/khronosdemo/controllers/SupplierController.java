package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.SupplierRequestDTO;
import com.moga.khronosdemo.dtos.response.SupplierResponseDTO;
import com.moga.khronosdemo.services.SupplierService;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The suppliers an establishment buys its stock from. Only the establishment's owner or an admin can read
 * or change them; workers never see them.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<SupplierResponseDTO> findByEstablishment(@PathVariable Long establishmentId) {
        return supplierService.findByEstablishment(establishmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public SupplierResponseDTO create(@Validated(OnCreate.class) @RequestBody SupplierRequestDTO request) {
        return supplierService.create(request);
    }

    // establishmentId in the body is ignored: a supplier stays where it was created
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsSupplier(#id)")
    public SupplierResponseDTO update(@PathVariable Long id, @Valid @RequestBody SupplierRequestDTO request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsSupplier(#id)")
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}

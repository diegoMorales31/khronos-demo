package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.EstablishmentRequestDTO;
import com.moga.khronosdemo.dtos.response.EstablishmentResponseDTO;
import com.moga.khronosdemo.services.EstablishmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/establishments")
public class EstablishmentController {

    private final EstablishmentService establishmentService;

    public EstablishmentController(EstablishmentService establishmentService) {
        this.establishmentService = establishmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public EstablishmentResponseDTO create(@Valid @RequestBody EstablishmentRequestDTO request) {
        return establishmentService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<EstablishmentResponseDTO> findAll() {
        return establishmentService.findAll();
    }

    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.isSelf(#ownerId)")
    public List<EstablishmentResponseDTO> findByOwner(@PathVariable Long ownerId) {
        return establishmentService.findByOwner(ownerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEstablishment(#id)")
    public EstablishmentResponseDTO findById(@PathVariable Long id) {
        return establishmentService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.canUpdateEstablishment(#id, #request.ownerId())")
    public EstablishmentResponseDTO update(@PathVariable Long id, @Valid @RequestBody EstablishmentRequestDTO request) {
        return establishmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void delete(@PathVariable Long id) {
        establishmentService.delete(id);
    }
}

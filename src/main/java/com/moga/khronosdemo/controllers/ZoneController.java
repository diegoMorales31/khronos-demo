package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.ZoneRequestDTO;
import com.moga.khronosdemo.dtos.response.ZoneResponseDTO;
import com.moga.khronosdemo.services.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#request.establishmentId())")
    public ZoneResponseDTO create(@Valid @RequestBody ZoneRequestDTO request) {
        return zoneService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<ZoneResponseDTO> findAll() {
        return zoneService.findAll();
    }

    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfEstablishment(#establishmentId)")
    public List<ZoneResponseDTO> findByEstablishment(@PathVariable Long establishmentId) {
        return zoneService.findByEstablishment(establishmentId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.memberOfZone(#id)")
    public ZoneResponseDTO findById(@PathVariable Long id) {
        return zoneService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsZone(#id)")
    public ZoneResponseDTO update(@PathVariable Long id, @Valid @RequestBody ZoneRequestDTO request) {
        return zoneService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsZone(#id)")
    public void delete(@PathVariable Long id) {
        zoneService.delete(id);
    }
}

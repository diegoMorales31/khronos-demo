package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.ProductActiveRequestDTO;
import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;
import com.moga.khronosdemo.services.ProductService;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The products an establishment keeps in stock, each with its stock worked out from its movements. Only
 * the establishment's owner or an admin can read or change them; workers never see them. A supplier named
 * in a body must be the caller's too.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // By category, then by name. Inactive products only with includeInactive=true
    @GetMapping("/establishment/{establishmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsEstablishment(#establishmentId)")
    public List<ProductResponseDTO> findByEstablishment(@PathVariable Long establishmentId,
                                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return productService.findByEstablishment(establishmentId, includeInactive);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsProduct(#id)")
    public ProductResponseDTO findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsEstablishment(#request.establishmentId())"
            + " and (#request.supplierId() == null or @access.ownsSupplier(#request.supplierId())))")
    public ProductResponseDTO create(@Validated(OnCreate.class) @RequestBody ProductRequestDTO request) {
        return productService.create(request);
    }

    // establishmentId in the body is ignored: a product stays where it was created
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsProduct(#id)"
            + " and (#request.supplierId() == null or @access.ownsSupplier(#request.supplierId())))")
    public ProductResponseDTO update(@PathVariable Long id, @Valid @RequestBody ProductRequestDTO request) {
        return productService.update(id, request);
    }

    // An inactive product takes no new movements or purchase order lines; it keeps its history
    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsProduct(#id)")
    public ProductResponseDTO setActive(@PathVariable Long id, @Valid @RequestBody ProductActiveRequestDTO request) {
        return productService.setActive(id, request.active());
    }

    // Only a product without stock movements; deactivate one that has them instead
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or @access.ownsProduct(#id)")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}

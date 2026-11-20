package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.StockCountRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.enums.PlanFeature;
import com.moga.khronosdemo.security.RequiresPlan;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.StockCountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A physical stock count: what was found on the shelves, product by product. It records a COUNT movement
 * per product with the difference from the stock. Only the establishment's owner or an admin can record
 * one, and every product named must be theirs too.
 */
@RestController
@RequiresPlan(PlanFeature.INVENTORY)
@RequestMapping("/api/stock-counts")
public class StockCountController {

    private final StockCountService stockCountService;

    public StockCountController(StockCountService stockCountService) {
        this.stockCountService = stockCountService;
    }

    // The COUNT movements recorded, in the order of the items
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (@access.ownsEstablishment(#request.establishmentId())"
            + " and @access.ownsProducts(#request.items().![productId()]))")
    public List<StockMovementResponseDTO> count(@Valid @RequestBody StockCountRequestDTO request,
                                                @AuthenticationPrincipal SecurityUser principal) {
        return stockCountService.count(request, principal.getId());
    }
}

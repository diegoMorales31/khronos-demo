package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.CrossFieldChecked;
import com.moga.khronosdemo.validation.CrossFieldValid;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Creates a draft, or replaces everything in one but its establishment
@CrossFieldValid
public record PurchaseOrderRequestDTO(
        // Only read when the order is created: an order never moves to another establishment
        @NotNull(groups = OnCreate.class)
        Long establishmentId,

        // Of the order's establishment
        @NotNull
        Long supplierId,

        LocalDate expectedDate,

        @Size(max = 500)
        String notes,

        // Each product once
        @NotNull @Size(min = 1, max = MAX_LINES)
        List<@Valid @NotNull PurchaseOrderLineRequestDTO> lines
) implements CrossFieldChecked {

    public static final int MAX_LINES = 200;

    @Override
    public Map<String, String> crossFieldErrors() {
        if (lines != null && CrossFieldChecked.hasRepeats(lines.stream()
                .map(line -> line != null ? line.productId() : null))) {
            return Map.of("lines", "must not contain the same product twice");
        }
        return Map.of();
    }
}

package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderLineRequestDTO(
        // Of the order's establishment, and active
        @NotNull
        Long productId,

        // In the product's unit, more than 0, with at most three decimals
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3)
        BigDecimal quantity,

        // Euros per unit, zero or more, with at most two decimals
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal unitCost
) {
}

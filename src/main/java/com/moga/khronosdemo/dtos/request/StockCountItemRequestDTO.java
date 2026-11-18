package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record StockCountItemRequestDTO(
        @NotNull
        Long productId,

        // What is physically there, in the product's unit, zero or more, with at most three decimals
        @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3)
        BigDecimal countedQuantity
) {
}

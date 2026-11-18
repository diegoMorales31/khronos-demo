package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderReceiveLineRequestDTO(
        // A line of the order being received
        @NotNull
        Long lineId,

        // What came in, in the product's unit, zero or more, with at most three decimals; 0 when it didn't come
        @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3)
        BigDecimal receivedQuantity,

        // The euros per unit actually paid; when left out, the line keeps the cost it was ordered at
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal unitCost
) {
}

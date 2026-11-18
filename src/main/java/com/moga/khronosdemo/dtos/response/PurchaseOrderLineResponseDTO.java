package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ProductUnit;

import java.math.BigDecimal;

public record PurchaseOrderLineResponseDTO(
        Long id,
        Long productId,
        String productName,
        ProductUnit unit,
        // Ordered, with three decimals
        BigDecimal quantity,
        // Euros per unit: the one ordered, or the one received when it was given; a line that came in
        // without either has its product's cost at the time it was received
        BigDecimal unitCost,
        // Null until the order is received
        BigDecimal receivedQuantity,
        // receivedQuantity (or quantity before receiving) x unitCost; null without a cost
        BigDecimal lineTotal
) {
}

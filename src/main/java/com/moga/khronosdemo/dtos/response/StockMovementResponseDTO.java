package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.entities.enums.StockMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record StockMovementResponseDTO(
        Long id,
        Long productId,
        String productName,
        ProductUnit unit,
        StockMovementType type,
        // Signed, with three decimals: positive comes in, negative goes out
        BigDecimal quantity,
        // The euros per unit the movement is valued at: the purchase's, or the product's at the time
        BigDecimal unitCost,
        // |quantity| x unitCost in euros, never negative; null without a cost. The stock-movements CSV
        // export gives it the quantity's sign instead
        BigDecimal value,
        String reason,
        // The business day the movement counts for
        LocalDate date,
        Instant createdAt,
        // Kept even if that account is deleted later
        String createdByName,
        // The purchase order this purchase was received with, if any
        Long purchaseOrderId,
        // The product's stock right after this movement was recorded
        BigDecimal stockAfter
) {
}

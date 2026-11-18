package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;

import java.math.BigDecimal;

public record ProductResponseDTO(
        Long id,
        Long establishmentId,
        String name,
        ProductCategory category,
        ProductUnit unit,
        BigDecimal minStock,
        // The last known cost of one unit, in euros
        BigDecimal unitCost,
        Long supplierId,
        String supplierName,
        Boolean isActive,
        // The sum of all the product's stock movements, with three decimals
        BigDecimal currentStock,
        // currentStock x unitCost in euros; null without a cost and 0.00 when the stock is 0 or less
        BigDecimal stockValue,
        // True when the product has a minimum and its stock is at or below it
        Boolean isLowStock
) {
}

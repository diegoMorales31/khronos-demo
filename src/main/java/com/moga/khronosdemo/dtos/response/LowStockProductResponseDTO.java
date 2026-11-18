package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ProductUnit;

import java.math.BigDecimal;

public record LowStockProductResponseDTO(
        Long productId,
        String productName,
        ProductUnit unit,
        BigDecimal currentStock,
        BigDecimal minStock,
        // The usual supplier, to know whom to order from
        Long supplierId,
        String supplierName
) {
}

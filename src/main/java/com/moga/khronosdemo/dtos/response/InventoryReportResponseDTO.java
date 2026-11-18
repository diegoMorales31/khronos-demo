package com.moga.khronosdemo.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Money is euros with two decimals. The stock figures are as of now; the values are of the movements dated
// in the range, each at the unit cost it kept, leaving out those without one
public record InventoryReportResponseDTO(
        LocalDate from,
        LocalDate to,
        String currency,
        // What the stock on hand of the active products is worth at their cost; those without a cost or
        // without stock on hand add nothing
        BigDecimal stockValue,
        // Active products
        Integer productsCount,
        Integer lowStockCount,
        Integer negativeStockCount,
        BigDecimal purchasesValue,
        // In positive
        BigDecimal wasteValue,
        // Net: negative when the counts found less than the ledger said
        BigDecimal countDifferenceValue,
        // The waste of the range by product, the largest value first, at most 10
        List<WasteByProductResponseDTO> wasteByProduct,
        // The active products at or below their minimum, by category and then name
        List<LowStockProductResponseDTO> lowStock
) {
}

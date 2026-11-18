package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ProductUnit;

import java.math.BigDecimal;

public record WasteByProductResponseDTO(
        Long productId,
        String productName,
        ProductUnit unit,
        // Wasted in the range, in positive, with three decimals
        BigDecimal quantity,
        // In euros, in positive; null when none of its waste had a cost
        BigDecimal value
) {
}

package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ExpenseCategory;

import java.math.BigDecimal;

public record CategoryAmountResponseDTO(
        ExpenseCategory category,
        BigDecimal amount
) {
}

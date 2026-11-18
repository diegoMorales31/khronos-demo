package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponseDTO(
        Long id,
        Long establishmentId,
        // The business day the expense counts for
        LocalDate date,
        ExpenseCategory category,
        String description,
        String supplier,
        // Euros, with two decimals
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String notes,
        Instant createdAt,
        // Kept even if that account is deleted later
        String createdByName,
        // The purchase order whose receipt created this expense, which then can't be edited; null otherwise
        Long purchaseOrderId
) {
}

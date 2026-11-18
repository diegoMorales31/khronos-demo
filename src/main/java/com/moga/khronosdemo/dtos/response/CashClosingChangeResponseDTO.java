package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.CashClosingAction;

import java.math.BigDecimal;
import java.time.Instant;

// totalSales, countedCash and difference are the closing's values right after the change; countedCash and
// difference are null when the cash had not been counted yet
public record CashClosingChangeResponseDTO(
        Long id,
        CashClosingAction action,
        Instant changedAt,
        // Null if the author's account has since been deleted; the name is kept
        Long changedByUserId,
        String changedByName,
        // The reason given to reopen, or for the difference when closing; null otherwise
        String reason,
        BigDecimal totalSales,
        BigDecimal countedCash,
        BigDecimal difference
) {
}

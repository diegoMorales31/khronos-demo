package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.CashClosingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Amounts are euros with two decimals. The four worked-out figures follow the day's cash expenses while
// the closing is a draft and keep the values it was closed with once it is closed.
public record CashClosingResponseDTO(
        Long id,
        Long establishmentId,
        LocalDate businessDate,
        CashClosingStatus status,
        BigDecimal openingFloat,
        BigDecimal cashSales,
        BigDecimal cardSales,
        BigDecimal otherSales,
        BigDecimal cashWithdrawals,
        // Null while a draft's cash has not been counted; a closed closing always has it
        BigDecimal countedCash,
        BigDecimal tips,
        String notes,
        // The day's expenses paid in cash
        BigDecimal cashExpenses,
        // cashSales + cardSales + otherSales
        BigDecimal totalSales,
        // openingFloat + cashSales - cashExpenses - cashWithdrawals
        BigDecimal expectedCash,
        // countedCash - expectedCash: negative when cash is missing; null while countedCash is null
        BigDecimal difference,
        String differenceReason,
        Instant closedAt,
        String closedByName,
        Instant createdAt,
        Instant updatedAt
) {
}

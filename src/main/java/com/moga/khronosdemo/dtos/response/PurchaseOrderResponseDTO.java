package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderResponseDTO(
        Long id,
        Long establishmentId,
        Long supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        // Set when it is ordered
        LocalDate orderDate,
        LocalDate expectedDate,
        LocalDate receivedDate,
        String notes,
        // By id, in the order they were added
        List<PurchaseOrderLineResponseDTO> lines,
        // The lines' totals added up; null when no line has a cost
        BigDecimal total,
        // The expense created when it was received; null if none was, or it was deleted since
        Long expenseId,
        Instant createdAt,
        // Kept even if that account is deleted later
        String createdByName
) {
}

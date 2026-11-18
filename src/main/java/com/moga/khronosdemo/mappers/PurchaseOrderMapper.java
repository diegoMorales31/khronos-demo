package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.response.PurchaseOrderLineResponseDTO;
import com.moga.khronosdemo.dtos.response.PurchaseOrderResponseDTO;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.PurchaseOrder;
import com.moga.khronosdemo.entities.PurchaseOrderLine;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class PurchaseOrderMapper {

    // The supplier, lines and products are read, so callers fetch them with the order. The establishment
    // and expense ids come from the foreign keys without loading either
    public PurchaseOrderResponseDTO toResponse(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        return new PurchaseOrderResponseDTO(
                order.getId(),
                order.getEstablishment().getId(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getStatus(),
                order.getOrderDate(),
                order.getExpectedDate(),
                order.getReceivedDate(),
                order.getNotes(),
                order.getLines().stream()
                        .sorted(Comparator.comparing(PurchaseOrderLine::getId))
                        .map(PurchaseOrderMapper::toResponse)
                        .toList(),
                order.total(),
                order.getExpense() != null ? order.getExpense().getId() : null,
                order.getCreatedAt(),
                order.getCreatedByName()
        );
    }

    private static PurchaseOrderLineResponseDTO toResponse(PurchaseOrderLine line) {
        Product product = line.getProduct();
        return new PurchaseOrderLineResponseDTO(
                line.getId(),
                product.getId(),
                product.getName(),
                product.getUnit(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getReceivedQuantity(),
                line.lineTotal()
        );
    }
}

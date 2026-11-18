package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.StockMovement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StockMovementMapper {

    // The product is read for its name and unit, so listings fetch it with the movements. The purchase
    // order's id comes from the foreign key without loading the order
    public StockMovementResponseDTO toResponse(StockMovement movement) {
        if (movement == null) {
            return null;
        }
        Product product = movement.getProduct();
        return new StockMovementResponseDTO(
                movement.getId(),
                product.getId(),
                product.getName(),
                product.getUnit(),
                movement.getType(),
                movement.getQuantity(),
                movement.getUnitCost(),
                value(movement.getQuantity(), movement.getUnitCost()),
                movement.getReason(),
                movement.getDate(),
                movement.getCreatedAt(),
                movement.getCreatedByName(),
                movement.getPurchaseOrder() != null ? movement.getPurchaseOrder().getId() : null,
                movement.getStockAfter()
        );
    }

    // |quantity| x unitCost, rounded to cents; null without a cost
    public static BigDecimal value(BigDecimal quantity, BigDecimal unitCost) {
        return unitCost != null ? quantity.abs().multiply(unitCost).setScale(2, RoundingMode.HALF_UP) : null;
    }
}

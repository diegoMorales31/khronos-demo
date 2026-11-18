package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.Supplier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ProductMapper {

    private static final BigDecimal NO_VALUE = BigDecimal.ZERO.setScale(2);

    /**
     * @param currentStock the sum of the product's movements, worked out by the caller (see StockLedger),
     *                     so a listing adds up every product's stock in one query
     */
    public ProductResponseDTO toResponse(Product product, BigDecimal currentStock) {
        if (product == null) {
            return null;
        }
        Supplier supplier = product.getSupplier();
        BigDecimal stock = currentStock.setScale(3, RoundingMode.UNNECESSARY);
        return new ProductResponseDTO(
                product.getId(),
                product.getEstablishment().getId(),
                product.getName(),
                product.getCategory(),
                product.getUnit(),
                product.getMinStock(),
                product.getUnitCost(),
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getName() : null,
                product.isActive(),
                stock,
                stockValue(stock, product.getUnitCost()),
                product.getMinStock() != null && stock.compareTo(product.getMinStock()) <= 0
        );
    }

    // Everything but the establishment, which an update ignores, and the supplier, which the service checks.
    // Quantities and costs are validated to three and two decimals, so setting the scale never rounds them
    public void updateEntity(Product product, ProductRequestDTO request) {
        product.setName(request.name().strip());
        product.setCategory(request.category());
        product.setUnit(request.unit());
        product.setMinStock(request.minStock() != null ? request.minStock().setScale(3, RoundingMode.UNNECESSARY) : null);
        product.setUnitCost(request.unitCost() != null ? request.unitCost().setScale(2, RoundingMode.UNNECESSARY) : null);
    }

    // What the stock on hand is worth at the product's cost: null without a cost, 0.00 with no stock on hand
    public static BigDecimal stockValue(BigDecimal stock, BigDecimal unitCost) {
        if (unitCost == null) {
            return null;
        }
        if (stock.signum() <= 0) {
            return NO_VALUE;
        }
        return stock.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }
}

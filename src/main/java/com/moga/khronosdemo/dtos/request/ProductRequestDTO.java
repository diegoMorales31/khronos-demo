package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDTO(
        // Only read when the product is created: a product never moves to another establishment
        @NotNull(groups = OnCreate.class)
        Long establishmentId,

        // Unique within the establishment, whatever its case
        @NotBlank @Size(max = 100)
        String name,

        @NotNull
        ProductCategory category,

        // Can't change once the product has stock movements
        @NotNull
        ProductUnit unit,

        // In the product's unit, zero or more, with at most three decimals; null means no minimum
        @DecimalMin("0") @Digits(integer = 9, fraction = 3)
        BigDecimal minStock,

        // Euros per unit, zero or more, with at most two decimals
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal unitCost,

        // The usual supplier, of the same establishment
        Long supplierId
) {
}

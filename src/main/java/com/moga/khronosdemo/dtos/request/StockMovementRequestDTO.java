package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.validation.CrossFieldChecked;
import com.moga.khronosdemo.validation.CrossFieldValid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@CrossFieldValid
public record StockMovementRequestDTO(
        @NotNull
        Long productId,

        // PURCHASE, WASTE, CONSUMPTION or ADJUSTMENT. A COUNT is only recorded by a stock count
        @NotNull
        StockMovementType type,

        // In the product's unit, with at most three decimals. More than 0 for PURCHASE, WASTE and
        // CONSUMPTION, whose type sets the sign; for ADJUSTMENT, signed and not 0
        @NotNull @Digits(integer = 9, fraction = 3)
        BigDecimal quantity,

        // Only for PURCHASE: euros per unit, which also becomes the product's unit cost
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal unitCost,

        // Required, 3 to 255 characters, for WASTE and ADJUSTMENT
        @Size(max = 255)
        String reason,

        // The business day; today in the establishment's time zone when left out, and never later
        LocalDate date
) implements CrossFieldChecked {

    public static final int MIN_REASON_LENGTH = 3;

    @Override
    public Map<String, String> crossFieldErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (type == null) {
            return errors;
        }
        switch (type) {
            case COUNT -> errors.put("type", "COUNT is only recorded by a stock count (POST /api/stock-counts)");
            case PURCHASE, WASTE, CONSUMPTION -> {
                if (quantity != null && quantity.signum() <= 0) {
                    errors.put("quantity", "must be greater than 0");
                }
            }
            case ADJUSTMENT -> {
                if (quantity != null && quantity.signum() == 0) {
                    errors.put("quantity", "must not be 0");
                }
            }
        }
        if (type == StockMovementType.WASTE || type == StockMovementType.ADJUSTMENT) {
            if (!StringUtils.hasText(reason)) {
                errors.put("reason", "must not be blank for WASTE and ADJUSTMENT");
            } else if (reason.strip().length() < MIN_REASON_LENGTH) {
                errors.put("reason", "size must be between 3 and 255");
            }
        }
        if (unitCost != null && type != StockMovementType.PURCHASE) {
            errors.put("unitCost", "can only be sent with a PURCHASE");
        }
        return errors;
    }
}

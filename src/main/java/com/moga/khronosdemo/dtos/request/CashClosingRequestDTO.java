package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// Amounts are euros, zero or more, with at most two decimals
public record CashClosingRequestDTO(
        // Only read when the closing is created, like businessDate
        @NotNull(groups = OnCreate.class)
        Long establishmentId,

        // The business day closed; it cannot be in the future
        @NotNull(groups = OnCreate.class)
        LocalDate businessDate,

        // Cash in the till when the day started
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal openingFloat,

        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal cashSales,

        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal cardSales,

        // Other payment methods (Bizum, vouchers...); 0 when left out
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal otherSales,

        // Cash taken out of the till during the day; 0 when left out
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal cashWithdrawals,

        // Cash counted in the till at closing time. Optional on a draft, which can be saved before the cash
        // is counted; the day can only be closed once it is set
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal countedCash,

        // Optional and informative only: tips are not sales
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal tips,

        @Size(max = 500)
        String notes
) {
}

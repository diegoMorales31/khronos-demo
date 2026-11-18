package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequestDTO(
        // Only read when the expense is created: an expense never moves to another establishment
        @NotNull(groups = OnCreate.class)
        Long establishmentId,

        // The business day the expense counts for; it cannot be in the future
        @NotNull
        LocalDate date,

        @NotNull
        ExpenseCategory category,

        @NotBlank @Size(max = 150)
        String description,

        @Size(max = 100)
        String supplier,

        // Euros, more than zero and with at most two decimals
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @NotNull
        PaymentMethod paymentMethod,

        @Size(max = 500)
        String notes
) {
}

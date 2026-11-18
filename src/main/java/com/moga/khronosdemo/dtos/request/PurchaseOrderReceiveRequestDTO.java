package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.validation.CrossFieldChecked;
import com.moga.khronosdemo.validation.CrossFieldValid;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossFieldValid
public record PurchaseOrderReceiveRequestDTO(
        // The business day it arrived; today in the establishment's time zone when left out, and never later
        LocalDate receivedDate,

        // Every line of the order, each once
        @NotNull @Size(min = 1, max = PurchaseOrderRequestDTO.MAX_LINES)
        List<@Valid @NotNull PurchaseOrderReceiveLineRequestDTO> lines,

        // Whether to record the total received as an expense
        @NotNull
        Boolean createExpense,

        // How the expense was paid; required when createExpense is true, ignored otherwise
        PaymentMethod paymentMethod
) implements CrossFieldChecked {

    @Override
    public Map<String, String> crossFieldErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (lines != null && CrossFieldChecked.hasRepeats(lines.stream()
                .map(line -> line != null ? line.lineId() : null))) {
            errors.put("lines", "must not contain the same line twice");
        }
        if (Boolean.TRUE.equals(createExpense) && paymentMethod == null) {
            errors.put("paymentMethod", "must not be null when createExpense is true");
        }
        return errors;
    }
}

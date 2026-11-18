package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Size;

// Why the counted cash differs from the expected cash. Required only when the difference is over the threshold
public record CashClosingCloseRequestDTO(
        @Size(max = 255)
        String differenceReason
) {
}

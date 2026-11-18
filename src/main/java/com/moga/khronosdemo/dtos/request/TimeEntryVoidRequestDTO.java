package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TimeEntryVoidRequestDTO(
        @NotBlank @Size(min = 3, max = 255)
        String reason
) {
}

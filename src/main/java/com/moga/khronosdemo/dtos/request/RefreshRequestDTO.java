package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequestDTO(
        @NotBlank @Size(max = 100)
        String refreshToken
) {
}

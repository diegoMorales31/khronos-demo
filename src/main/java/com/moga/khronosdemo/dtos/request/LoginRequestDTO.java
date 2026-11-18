package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank @Size(max = 255)
        String username,
        @NotBlank @MaxBytes(72)
        String password
) {
}

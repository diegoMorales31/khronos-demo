package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.MaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDTO(
        @NotBlank @Size(min = 8) @MaxBytes(72)
        String newPassword
) {
}

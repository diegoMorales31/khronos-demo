package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequestDTO(
        @NotBlank @Email @Size(max = 255)
        String to,

        @NotBlank @Size(max = 255)
        String subject,

        @NotBlank
        String html
) {
}

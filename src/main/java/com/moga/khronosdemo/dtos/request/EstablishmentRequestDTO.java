package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EstablishmentRequestDTO(
        @NotBlank @Size(max = 100)
        String establishmentName,

        @NotBlank @Size(max = 255)
        String establishmentAddress,

        @NotBlank @Size(max = 100)
        String ownerName,

        @NotBlank @Email @Size(max = 255)
        String ownerEmail,

        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "must be a valid phone number")
        String ownerPhone
) {
}

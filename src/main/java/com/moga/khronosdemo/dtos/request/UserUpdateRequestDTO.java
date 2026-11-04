package com.moga.khronosdemo.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDTO(
        @NotBlank @Size(min = 3, max = 50)
        String username,

        @NotBlank @Size(max = 50)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "must be a valid phone number")
        String phoneNumber,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotNull
        Long establishmentId
) {
}

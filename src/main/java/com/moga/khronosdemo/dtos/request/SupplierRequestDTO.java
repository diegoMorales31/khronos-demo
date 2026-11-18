package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.OnCreate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierRequestDTO(
        // Only read when the supplier is created: a supplier never moves to another establishment
        @NotNull(groups = OnCreate.class)
        Long establishmentId,

        // Unique within the establishment, whatever its case
        @NotBlank @Size(max = 100)
        String name,

        @Size(max = 30)
        String phone,

        @Email @Size(max = 255)
        String email,

        @Size(max = 500)
        String notes
) {
}

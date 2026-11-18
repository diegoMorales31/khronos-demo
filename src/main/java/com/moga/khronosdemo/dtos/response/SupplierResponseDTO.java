package com.moga.khronosdemo.dtos.response;

public record SupplierResponseDTO(
        Long id,
        Long establishmentId,
        String name,
        String phone,
        String email,
        String notes
) {
}

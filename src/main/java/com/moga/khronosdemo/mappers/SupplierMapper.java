package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.dtos.request.SupplierRequestDTO;
import com.moga.khronosdemo.dtos.response.SupplierResponseDTO;
import com.moga.khronosdemo.entities.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SupplierMapper {

    // The establishment id comes from the foreign key without loading the establishment
    public SupplierResponseDTO toResponse(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return new SupplierResponseDTO(
                supplier.getId(),
                supplier.getEstablishment().getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getNotes()
        );
    }

    // Everything but the establishment, which an update ignores. Blank optional fields are stored as null
    public void updateEntity(Supplier supplier, SupplierRequestDTO request) {
        supplier.setName(request.name().strip());
        supplier.setPhone(clean(request.phone()));
        supplier.setEmail(clean(request.email()));
        supplier.setNotes(clean(request.notes()));
    }

    private static String clean(String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.SupplierRequestDTO;
import com.moga.khronosdemo.dtos.response.SupplierResponseDTO;

import java.util.List;

public interface SupplierService {

    List<SupplierResponseDTO> findByEstablishment(Long establishmentId);

    SupplierResponseDTO create(SupplierRequestDTO request);

    SupplierResponseDTO update(Long id, SupplierRequestDTO request);

    void delete(Long id);
}

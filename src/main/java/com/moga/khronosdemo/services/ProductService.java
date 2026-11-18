package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.response.ProductResponseDTO;

import java.util.List;

public interface ProductService {

    List<ProductResponseDTO> findByEstablishment(Long establishmentId, boolean includeInactive);

    ProductResponseDTO findById(Long id);

    ProductResponseDTO create(ProductRequestDTO request);

    ProductResponseDTO update(Long id, ProductRequestDTO request);

    ProductResponseDTO setActive(Long id, boolean active);

    void delete(Long id);
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.StockMovementRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.enums.StockMovementType;

import java.time.LocalDate;
import java.util.List;

public interface StockMovementService {

    List<StockMovementResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                       Long productId, StockMovementType type);

    StockMovementResponseDTO create(StockMovementRequestDTO request, Long actorId);
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.StockCountRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;

import java.util.List;

public interface StockCountService {

    List<StockMovementResponseDTO> count(StockCountRequestDTO request, Long actorId);
}

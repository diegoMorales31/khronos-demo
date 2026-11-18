package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderRequestDTO;
import com.moga.khronosdemo.dtos.response.PurchaseOrderResponseDTO;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseOrderService {

    List<PurchaseOrderResponseDTO> findByEstablishment(Long establishmentId, PurchaseOrderStatus status,
                                                       LocalDate from, LocalDate to);

    PurchaseOrderResponseDTO findById(Long id);

    PurchaseOrderResponseDTO create(PurchaseOrderRequestDTO request, Long actorId);

    PurchaseOrderResponseDTO update(Long id, PurchaseOrderRequestDTO request);

    void delete(Long id);

    PurchaseOrderResponseDTO order(Long id);

    PurchaseOrderResponseDTO cancel(Long id);

    PurchaseOrderResponseDTO receive(Long id, PurchaseOrderReceiveRequestDTO request, Long actorId);
}

package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface CashClosingService {

    List<CashClosingResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to);

    CashClosingResponseDTO findById(Long id);

    CashClosingResponseDTO create(CashClosingRequestDTO request, Long actorId);

    CashClosingResponseDTO update(Long id, CashClosingRequestDTO request, Long actorId);

    CashClosingResponseDTO close(Long id, String differenceReason, Long actorId);

    CashClosingResponseDTO reopen(Long id, String reason, Long actorId);

    void delete(Long id);

    List<CashClosingChangeResponseDTO> findHistory(Long id);
}

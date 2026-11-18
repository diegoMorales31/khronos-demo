package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseService {

    List<ExpenseResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                 ExpenseCategory category);

    ExpenseResponseDTO create(ExpenseRequestDTO request, Long actorId);

    ExpenseResponseDTO update(Long id, ExpenseRequestDTO request);

    void delete(Long id);
}

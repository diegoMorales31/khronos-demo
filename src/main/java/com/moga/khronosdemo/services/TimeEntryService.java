package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ClockInRequestDTO;
import com.moga.khronosdemo.dtos.request.ClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryCorrectionRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryRequestDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimeEntryService {

    Optional<TimeEntryResponseDTO> findOpenOfWorker(Long workerId);

    TimeEntryResponseDTO clockIn(Long workerId, ClockInRequestDTO request);

    TimeEntryResponseDTO clockOut(Long workerId, ClockOutRequestDTO request);

    TimeEntryResponseDTO create(TimeEntryRequestDTO request, Long actorId);

    TimeEntryResponseDTO correct(Long id, TimeEntryCorrectionRequestDTO request, Long actorId);

    TimeEntryResponseDTO clockOutEntry(Long id, TimeEntryClockOutRequestDTO request, Long actorId);

    TimeEntryResponseDTO voidEntry(Long id, String reason, Long actorId);

    List<TimeEntryChangeResponseDTO> findHistory(Long id);

    List<TimeEntryResponseDTO> findByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeVoided);

    List<TimeEntryResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                   boolean includeVoided);

    List<TimeEntryResponseDTO> findOpenByEstablishment(Long establishmentId);
}

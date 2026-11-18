package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.response.HoursReportResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerDaysReportResponseDTO;

import java.time.LocalDate;

public interface ReportService {

    HoursReportResponseDTO hoursByEstablishment(Long establishmentId, LocalDate from, LocalDate to);

    WorkerDaysReportResponseDTO daysByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeCosts);

    // CSV files, as UTF-8 bytes with a byte order mark

    byte[] hoursCsv(Long establishmentId, LocalDate from, LocalDate to);

    byte[] timeEntriesCsvByEstablishment(Long establishmentId, LocalDate from, LocalDate to, boolean includeVoided);

    byte[] timeEntriesCsvByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeVoided);
}

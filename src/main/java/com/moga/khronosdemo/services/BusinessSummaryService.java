package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.response.BusinessSummaryResponseDTO;

import java.time.LocalDate;

public interface BusinessSummaryService {

    BusinessSummaryResponseDTO summary(Long establishmentId, LocalDate from, LocalDate to);

    // CSV files, as UTF-8 bytes with a byte order mark

    byte[] cashClosingsCsv(Long establishmentId, LocalDate from, LocalDate to);

    byte[] expensesCsv(Long establishmentId, LocalDate from, LocalDate to);

    byte[] summaryCsv(Long establishmentId, LocalDate from, LocalDate to);
}

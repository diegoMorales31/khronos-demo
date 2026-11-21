package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.response.CsvFileResponseDTO;
import com.moga.khronosdemo.dtos.response.InventoryReportResponseDTO;

import java.time.LocalDate;

public interface InventoryReportService {

    InventoryReportResponseDTO inventory(Long establishmentId, LocalDate from, LocalDate to);

    // The stock now, one row per active product, named after today in the establishment's time zone
    CsvFileResponseDTO inventoryCsv(Long establishmentId);

    byte[] stockMovementsCsv(Long establishmentId, LocalDate from, LocalDate to);
}

package com.moga.khronosdemo.dtos.response;

// A CSV export with the name it is downloaded as, for exports whose name the service decides
public record CsvFileResponseDTO(String filename, byte[] content) {
}

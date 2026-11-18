package com.moga.khronosdemo.services;

// A CSV export with the name it is downloaded as, for exports whose name the service decides
public record CsvFile(String filename, byte[] content) {
}

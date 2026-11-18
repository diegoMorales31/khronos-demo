package com.moga.khronosdemo.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        ErrorCode code,
        // What a coded error is about, so clients needn't parse the message, e.g. { "date": "2030-05-10" }
        Map<String, String> details
) {
}

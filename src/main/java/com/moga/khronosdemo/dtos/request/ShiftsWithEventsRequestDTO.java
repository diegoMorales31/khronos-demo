package com.moga.khronosdemo.dtos.request;

import com.moga.khronosdemo.validation.MaxBatchSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// New shifts and the events to put workers on, saved together; an assignment can rely on a shift of the same request
public record ShiftsWithEventsRequestDTO(
        @NotEmpty @MaxBatchSize
        List<@Valid WorkerShiftRequestDTO> shifts,
        @NotNull @MaxBatchSize
        List<@Valid WorkerEventRequestDTO> assignments
) {
}

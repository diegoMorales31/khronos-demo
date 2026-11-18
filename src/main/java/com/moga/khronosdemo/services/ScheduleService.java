package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ShiftsWithEventsRequestDTO;
import com.moga.khronosdemo.dtos.response.ShiftsWithEventsResponseDTO;

public interface ScheduleService {

    // Creates the shifts, then the event assignments, all or nothing, and emails each worker once with both
    ShiftsWithEventsResponseDTO createShiftsWithEvents(ShiftsWithEventsRequestDTO request);
}

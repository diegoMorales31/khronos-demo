package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.WorkerShift;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftDetails(LocalDate date, LocalTime startTime, LocalTime endTime, String zoneName) {

    public static ShiftDetails of(WorkerShift shift) {
        return new ShiftDetails(shift.getDate(), shift.getStartTime(), shift.getEndTime(), shift.getZone().getName());
    }
}

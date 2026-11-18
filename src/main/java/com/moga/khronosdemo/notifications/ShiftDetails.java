package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.WorkerShift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftDetails(LocalDate date, LocalTime startTime, LocalTime endTime, String zoneName) {

    public static ShiftDetails of(WorkerShift shift) {
        return new ShiftDetails(shift.getDate(), shift.getStartTime(), shift.getEndTime(), shift.getZone().getName());
    }

    // The shift runs past midnight, as in 18:00-02:00
    public boolean endsNextDay() {
        return WorkerShift.endsNextDay(startTime, endTime);
    }

    public LocalDateTime startDateTime() {
        return date.atTime(startTime);
    }

    public LocalDateTime endDateTime() {
        return WorkerShift.endOf(date, startTime, endTime);
    }
}

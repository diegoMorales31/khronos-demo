package com.moga.khronosdemo.entities.enums;

// How a day went for one worker in the daily report. The first that applies wins, in this order.
public enum WorkDayStatus {
    // An entry of the day is still open
    OPEN,
    // A shift of the day has ended and no entry is matched to it, even if the day's other shifts have one
    MISSED,
    // An entry of the day is not matched to any shift; always the case when the day has no shift
    UNPLANNED,
    // The first entry matched to a shift clocked in later than the late threshold
    LATE,
    // A shift of the day has not ended yet and no entry is matched to it
    UPCOMING,
    OK
}

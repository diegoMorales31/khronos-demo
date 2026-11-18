package com.moga.khronosdemo.entities.enums;

// Where a business day of the summary stands with its cash closing
public enum DayClosingStatus {
    // The day's closing is closed
    CLOSED,
    // The day has a closing that is still a draft
    DRAFT,
    // A day before today with a shift or a time entry and no closing at all
    MISSING,
    // No closing and nothing to close yet: no shift or entry, or the day is today or later
    NONE
}

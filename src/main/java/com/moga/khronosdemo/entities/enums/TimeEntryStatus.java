package com.moga.khronosdemo.entities.enums;

public enum TimeEntryStatus {
    // Clocked in and not clocked out yet
    OPEN,
    CLOSED,
    // Cancelled by an owner or admin; kept for the record but left out of totals
    VOIDED
}

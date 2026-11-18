package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.TimeEntryAction;

import java.time.Instant;

// clockIn, clockOut and breakMinutes are the entry's values right after the change
public record TimeEntryChangeResponseDTO(
        Long id,
        TimeEntryAction action,
        Instant changedAt,
        // Null if the author's account has since been deleted; the name is kept
        Long changedByUserId,
        String changedByName,
        String reason,
        Instant clockIn,
        Instant clockOut,
        int breakMinutes
) {
}

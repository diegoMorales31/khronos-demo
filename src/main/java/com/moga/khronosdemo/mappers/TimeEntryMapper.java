package com.moga.khronosdemo.mappers;

import com.moga.khronosdemo.config.TimeTrackingProperties;
import com.moga.khronosdemo.dtos.response.TimeEntryChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryResponseDTO;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.TimeEntryChange;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class TimeEntryMapper {

    private final Clock clock;
    private final TimeTrackingProperties properties;

    public TimeEntryMapper(Clock clock, TimeTrackingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    // Reads the worker's name, so listings fetch the worker and their account with the entries.
    // The shift and establishment ids come from the foreign keys without loading those rows.
    public TimeEntryResponseDTO toResponse(TimeEntry entry) {
        if (entry == null) {
            return null;
        }
        Worker worker = entry.getWorker();
        User user = worker.getUser();
        boolean stale = entry.isOpen()
                && entry.getClockIn().plus(properties.staleAfter()).isBefore(clock.instant());
        return new TimeEntryResponseDTO(
                entry.getId(),
                worker.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                entry.getEstablishment().getId(),
                entry.getBusinessDate(),
                entry.getClockIn(),
                entry.getClockOut(),
                entry.getBreakMinutes(),
                entry.getWorkedMinutes(),
                entry.getShift() != null ? entry.getShift().getId() : null,
                entry.getSource(),
                entry.getStatus(),
                entry.isEdited(),
                stale,
                entry.getNote()
        );
    }

    public TimeEntryChangeResponseDTO toResponse(TimeEntryChange change) {
        if (change == null) {
            return null;
        }
        return new TimeEntryChangeResponseDTO(
                change.getId(),
                change.getAction(),
                change.getChangedAt(),
                change.getChangedBy() != null ? change.getChangedBy().getId() : null,
                change.getChangedByName(),
                change.getReason(),
                change.getClockIn(),
                change.getClockOut(),
                change.getBreakMinutes()
        );
    }
}

package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.TimeEntryAction;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * One row of a time entry's history: what was done, by whom, when and why, and the clock-in,
 * clock-out and break the entry had right after. Rows are only ever added; a database trigger
 * rejects updates (see V7__time_entries.sql).
 */
@Entity
@Immutable
@Table(name = "time_entry_changes")
public class TimeEntryChange {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // An entry with history cannot be deleted, not even in the database (see V8)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_entry_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private TimeEntry timeEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimeEntryAction action;

    @Column(nullable = false)
    private Instant changedAt;

    // Deleting the author's account clears this link; their name below stays
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User changedBy;

    @Column(nullable = false, length = 511)
    private String changedByName;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant clockIn;

    private Instant clockOut;

    @Column(nullable = false)
    private int breakMinutes;

    public TimeEntryChange() {
    }

    public TimeEntryChange(TimeEntry timeEntry, TimeEntryAction action, Instant changedAt, User changedBy,
                           String changedByName, String reason) {
        this.timeEntry = timeEntry;
        this.action = action;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.changedByName = changedByName;
        this.reason = reason;
        this.clockIn = timeEntry.getClockIn();
        this.clockOut = timeEntry.getClockOut();
        this.breakMinutes = timeEntry.getBreakMinutes();
    }

    public Long getId() {
        return id;
    }

    public TimeEntry getTimeEntry() {
        return timeEntry;
    }

    public TimeEntryAction getAction() {
        return action;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public String getReason() {
        return reason;
    }

    public Instant getClockIn() {
        return clockIn;
    }

    public Instant getClockOut() {
        return clockOut;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }
}

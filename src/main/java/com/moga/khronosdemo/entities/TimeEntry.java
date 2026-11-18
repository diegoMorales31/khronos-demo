package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.TimeEntrySource;
import com.moga.khronosdemo.entities.enums.TimeEntryStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One clock-in and its clock-out, as UTC instants. Entries are never deleted through the API: they
 * are corrected or voided, and each change is kept in {@link TimeEntryChange}.
 */
@Entity
@Table(name = "time_entries")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // The database refuses to delete a worker or an establishment that has entries (see V8), and so
    // does the service, with a 409 that suggests deactivating the worker instead
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Establishment establishment;

    // The planned shift the clock-in was matched to. Deleting the shift keeps the entry and clears the link
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private WorkerShift shift;

    // The day of service the work counts for: the matched shift's date, or the local date of the clock-in
    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private Instant clockIn;

    private Instant clockOut;

    // Unpaid breaks, subtracted from the time between clock-in and clock-out
    @Column(nullable = false)
    private int breakMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimeEntrySource source;

    // True once the entry has been corrected
    @Column(nullable = false)
    private boolean edited;

    private Instant voidedAt;

    @Column(length = 255)
    private String note;

    @Column(nullable = false)
    private Instant createdAt;

    public TimeEntry() {
    }

    public TimeEntry(Worker worker, Instant clockIn, TimeEntrySource source, String note, Instant createdAt) {
        this.worker = worker;
        this.establishment = worker.getEstablishment();
        this.clockIn = clockIn;
        this.source = source;
        this.note = note;
        this.createdAt = createdAt;
    }

    public TimeEntryStatus getStatus() {
        if (voidedAt != null) {
            return TimeEntryStatus.VOIDED;
        }
        return clockOut == null ? TimeEntryStatus.OPEN : TimeEntryStatus.CLOSED;
    }

    public boolean isOpen() {
        return getStatus() == TimeEntryStatus.OPEN;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    // Whole minutes between clock-in and clock-out, minus breaks; null while the worker is still in
    public Integer getWorkedMinutes() {
        if (clockOut == null) {
            return null;
        }
        return (int) (Duration.between(clockIn, clockOut).toMinutes() - breakMinutes);
    }

    public Long getId() {
        return id;
    }

    public Worker getWorker() {
        return worker;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public WorkerShift getShift() {
        return shift;
    }

    public void setShift(WorkerShift shift) {
        this.shift = shift;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public Instant getClockIn() {
        return clockIn;
    }

    public void setClockIn(Instant clockIn) {
        this.clockIn = clockIn;
    }

    public Instant getClockOut() {
        return clockOut;
    }

    public void setClockOut(Instant clockOut) {
        this.clockOut = clockOut;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public TimeEntrySource getSource() {
        return source;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(Instant voidedAt) {
        this.voidedAt = voidedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

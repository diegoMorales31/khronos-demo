package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A block of work. The date is the business day: the day the shift starts. When the end time is not
 * after the start time the shift ends on the next day, so 18:00-02:00 on a Friday runs until 02:00 on Saturday.
 */
@Entity
@Table(name="worker_shifts")
public class WorkerShift {

    public static final long MAX_DURATION_MINUTES = 16 * 60;

    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDate date;

    private LocalTime startTime;
    private LocalTime endTime;

    // The real interval as local date-times, computed by PostgreSQL from the three fields above
    // (see V5__shift_intervals.sql). Only the overlap and coverage queries read them; Java code uses
    // getStartDateTime() and getEndDateTime(), which are always up to date.
    @Column(insertable = false, updatable = false)
    private LocalDateTime startsAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime endsAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    public WorkerShift() {
    }

    public WorkerShift(Worker worker, Zone zone, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.worker = worker;
        this.zone = zone;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public boolean endsNextDay() {
        return endsNextDay(startTime, endTime);
    }

    public long getDurationMinutes() {
        return durationMinutes(startTime, endTime);
    }

    public LocalDateTime getStartDateTime() {
        return date.atTime(startTime);
    }

    public LocalDateTime getEndDateTime() {
        return endOf(date, startTime, endTime);
    }

    // A shift whose end time is not after its start time finishes on the next day
    public static boolean endsNextDay(LocalTime startTime, LocalTime endTime) {
        return !endTime.isAfter(startTime);
    }

    public static LocalDateTime endOf(LocalDate date, LocalTime startTime, LocalTime endTime) {
        return (endsNextDay(startTime, endTime) ? date.plusDays(1) : date).atTime(endTime);
    }

    // Wall-clock length: 18:00-02:00 is 480 minutes. Equal times would be a whole day, which validation rejects.
    public static long durationMinutes(LocalTime startTime, LocalTime endTime) {
        long seconds = Duration.between(startTime, endTime).getSeconds();
        return (endsNextDay(startTime, endTime) ? seconds + SECONDS_PER_DAY : seconds) / 60;
    }
}

package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name="worker_shifts")
public class WorkerShift {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    private LocalDate date;

    private LocalTime startTime;
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    private WorkZone eventZone;

    public WorkerShift() {
    }

    public WorkerShift(Long id, Worker worker, LocalDate date, LocalTime startTime, LocalTime endTime, WorkZone eventZone) {
        this.id = id;
        this.worker = worker;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.eventZone = eventZone;
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

    public WorkZone getEventZone() {
        return eventZone;
    }

    public void setEventZone(WorkZone eventZone) {
        this.eventZone = eventZone;
    }

}

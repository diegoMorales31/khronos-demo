package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

@Entity
@Table(name="worker_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"worker_id", "event_id"}))
public class WorkerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    public WorkerEvent() {
    }

    public WorkerEvent(Worker worker, Event event) {
        this.worker = worker;
        this.event = event;
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

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }
}

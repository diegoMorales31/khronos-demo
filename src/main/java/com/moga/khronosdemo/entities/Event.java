package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;

    private LocalDateTime dateTime;

    private Integer peopleNumber;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkerEvent> workerEvents = new ArrayList<>();

    public Event() {
    }

    public Event(String title, LocalDateTime dateTime,
                 Integer peopleNumber, Zone zone, String description) {
        this.title = title;
        this.dateTime = dateTime;
        this.peopleNumber = peopleNumber;
        this.zone = zone;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public Integer getPeopleNumber() {
        return peopleNumber;
    }

    public void setPeopleNumber(Integer peopleNumber) {
        this.peopleNumber = peopleNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Establishment getEstablishment() {
        return zone != null ? zone.getEstablishment() : null;
    }

    public List<WorkerEvent> getWorkerEvents() {
        return workerEvents;
    }

    public void setWorkerEvents(List<WorkerEvent> workerEvents) {
        this.workerEvents = workerEvents;
    }

}

package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDateTime dateTime;

    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    private WorkZone workZone;

    private Integer peopleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id")
    private Establishment establishment;

    private String observation;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkerEvent> workerEvents = new ArrayList<>();

    public Event() {
    }

    public Event(LocalDateTime dateTime, LocalTime endTime, WorkZone workZone,
                 Integer peopleNumber, Establishment establishment, String observation) {
        this.dateTime = dateTime;
        this.endTime = endTime;
        this.workZone = workZone;
        this.peopleNumber = peopleNumber;
        this.establishment = establishment;
        this.observation = observation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public WorkZone getWorkZone() {
        return workZone;
    }

    public void setWorkZone(WorkZone workZone) {
        this.workZone = workZone;
    }

    public Integer getPeopleNumber() {
        return peopleNumber;
    }

    public void setPeopleNumber(Integer peopleNumber) {
        this.peopleNumber = peopleNumber;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public void setEstablishment(Establishment establishment) {
        this.establishment = establishment;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public void setWorkerEvents(List<WorkerEvent> workerEvents) {
        this.workerEvents = workerEvents;
    }

    public List<WorkerEvent> getWorkerEvents() {
        return workerEvents;
    }

}

package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="workers")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private WorkZone workZone;

    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkerShift> workerShifts = new ArrayList<>();

    @OneToMany(mappedBy = "worker")
    private List<WorkerEvent> workerEvents = new ArrayList<>();

    public Worker() {
    }

    public Worker(User user, WorkZone workZone) {
        this.user = user;
        this.workZone = workZone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public WorkZone getWorkZone() {
        return workZone;
    }

    public void setWorkZone(WorkZone workZone) {
        this.workZone = workZone;
    }

    public List<WorkerShift> getWorkerShifts() {
        return workerShifts;
    }

    public List<WorkerEvent> getWorkerEvents() {
        return workerEvents;
    }
}

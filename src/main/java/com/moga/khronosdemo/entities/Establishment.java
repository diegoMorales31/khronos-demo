package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="establishments")
public class Establishment {

    public static final String DEFAULT_TIME_ZONE = "Europe/Madrid";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String establishmentName;

    @Column(unique = true)
    private String establishmentAddress;

    // IANA time zone used to turn clock-in instants into local dates and times
    @Column(nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "establishment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zone> zones = new ArrayList<>();

    @OneToMany(mappedBy = "establishment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Worker> workers = new ArrayList<>();

    public Establishment() {
    }

    public Establishment(String establishmentName, String establishmentAddress, User owner) {
        this.establishmentName = establishmentName;
        this.establishmentAddress = establishmentAddress;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEstablishmentName() {
        return establishmentName;
    }

    public void setEstablishmentName(String establishmentName) {
        this.establishmentName = establishmentName;
    }

    public String getEstablishmentAddress() {
        return establishmentAddress;
    }

    public void setEstablishmentAddress(String establishmentAddress) {
        this.establishmentAddress = establishmentAddress;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timeZone);
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<Zone> getZones() {
        return zones;
    }

    public List<Worker> getWorkers() {
        return workers;
    }
}

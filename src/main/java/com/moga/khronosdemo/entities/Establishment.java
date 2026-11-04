package com.moga.khronosdemo.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="establishments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"establishmentAddress"}))

public class Establishment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String establishmentName;
    private String establishmentAddress;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    @OneToMany(mappedBy = "establishment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Event> events = new ArrayList<>();

    @OneToMany(mappedBy = "establishment")
    private List<User> users = new ArrayList<>();

    public Establishment() {
    }

    public Establishment(
            Long id, String establishmentName, String establishmentAddress,
            String ownerName, String ownerEmail, String ownerPhone,
            List<Event> events, List<User> users
    ) {
        this.id = id;
        this.establishmentName = establishmentName;
        this.establishmentAddress = establishmentAddress;
        this.ownerName = ownerName;
        this.ownerEmail = ownerEmail;
        this.ownerPhone = ownerPhone;
        this.events = events;
        this.users = users;
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

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone) {
        this.ownerPhone = ownerPhone;
    }

    public List<Event> getEvents() {
        return events;
    }

    public List<User> getUsers() {
        return users;
    }
}

package com.moga.khronosdemo.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Someone an establishment buys its stock from. The name is unique within the establishment whatever its
 * case (see V12__suppliers.sql).
 */
@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Never changes after the supplier is created
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Establishment establishment;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String notes;

    public Supplier() {
    }

    public Supplier(Establishment establishment) {
        this.establishment = establishment;
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

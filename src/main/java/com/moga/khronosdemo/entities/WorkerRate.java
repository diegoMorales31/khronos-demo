package com.moga.khronosdemo.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a worker costs per hour, in euros, from a given day until the next rate starts.
 * Rates are never edited; a change of pay is a new rate.
 */
@Entity
@Table(
        name = "worker_rates",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"worker_id", "effective_from"})
        }
)
public class WorkerRate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Deleting the worker deletes their rates in the database
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Worker worker;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private Instant createdAt;

    public WorkerRate() {
    }

    public WorkerRate(Worker worker, BigDecimal hourlyRate, LocalDate effectiveFrom, Instant createdAt) {
        this.worker = worker;
        this.hourlyRate = hourlyRate;
        this.effectiveFrom = effectiveFrom;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Worker getWorker() {
        return worker;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

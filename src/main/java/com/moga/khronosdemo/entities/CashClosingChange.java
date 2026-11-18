package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.CashClosingAction;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of a cash closing's history: what was done, by whom, when and why, and the closing's amounts
 * and figures right after. Rows are only ever added; a database trigger rejects updates (see
 * V10__cash_closings.sql).
 */
@Entity
@Immutable
@Table(name = "cash_closing_changes")
public class CashClosingChange {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // A closing with history cannot be deleted in the database (see V10); the service only deletes the
    // history of a draft that was never closed, together with the draft
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_closing_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private CashClosing cashClosing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CashClosingAction action;

    @Column(nullable = false)
    private Instant changedAt;

    // Deleting the author's account clears this link; their name below stays
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User changedBy;

    @Column(nullable = false, length = 511)
    private String changedByName;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingFloat;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cardSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal otherSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashWithdrawals;

    // Null, like the difference, when the closing's cash had not been counted yet (see V11)
    @Column(precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal tips;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashExpenses;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal difference;

    public CashClosingChange() {
    }

    public CashClosingChange(CashClosing closing, CashClosingAction action, Instant changedAt, User changedBy,
                             String reason, CashClosing.Figures figures) {
        this.cashClosing = closing;
        this.action = action;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.changedByName = changedBy.displayName();
        this.reason = reason;
        this.openingFloat = closing.getOpeningFloat();
        this.cashSales = closing.getCashSales();
        this.cardSales = closing.getCardSales();
        this.otherSales = closing.getOtherSales();
        this.cashWithdrawals = closing.getCashWithdrawals();
        this.countedCash = closing.getCountedCash();
        this.tips = closing.getTips();
        this.cashExpenses = figures.cashExpenses();
        this.totalSales = figures.totalSales();
        this.expectedCash = figures.expectedCash();
        this.difference = figures.difference();
    }

    public Long getId() {
        return id;
    }

    public CashClosing getCashClosing() {
        return cashClosing;
    }

    public CashClosingAction getAction() {
        return action;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getChangedByName() {
        return changedByName;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getOpeningFloat() {
        return openingFloat;
    }

    public BigDecimal getCashSales() {
        return cashSales;
    }

    public BigDecimal getCardSales() {
        return cardSales;
    }

    public BigDecimal getOtherSales() {
        return otherSales;
    }

    public BigDecimal getCashWithdrawals() {
        return cashWithdrawals;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public BigDecimal getTips() {
        return tips;
    }

    public BigDecimal getCashExpenses() {
        return cashExpenses;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public BigDecimal getExpectedCash() {
        return expectedCash;
    }

    public BigDecimal getDifference() {
        return difference;
    }
}

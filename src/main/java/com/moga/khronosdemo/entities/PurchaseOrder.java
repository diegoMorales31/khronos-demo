package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An order of products to one supplier. It goes DRAFT -> ORDERED -> RECEIVED, or is CANCELLED from DRAFT
 * or ORDERED; only a draft can be edited or deleted. Receiving it records its stock movements and, if
 * asked, an expense (see PurchaseOrderServiceImpl and V14__purchase_orders.sql).
 */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Never changes. An establishment with orders can't be deleted (see V14)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Establishment establishment;

    // A supplier with orders can't be deleted
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    // The business day it was ordered, its expected delivery, and the business day it was received
    private LocalDate orderDate;

    private LocalDate expectedDate;

    private LocalDate receivedDate;

    @Column(length = 500)
    private String notes;

    // The expense created when it was received, if one was asked for. Deleting that expense clears this
    // link and keeps the order and its movements
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Expense expense;

    @Column(nullable = false)
    private Instant createdAt;

    // Deleting the account that created the order clears this link; the name below stays
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(nullable = false, length = 511)
    private String createdByName;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public PurchaseOrder() {
    }

    public PurchaseOrder(Establishment establishment, User createdBy, Instant createdAt) {
        this.establishment = establishment;
        this.createdBy = createdBy;
        this.createdByName = createdBy.displayName();
        this.createdAt = createdAt;
    }

    // The lines' totals added up; null when no line has a cost
    public BigDecimal total() {
        return lines.stream()
                .map(PurchaseOrderLine::lineTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Expense getExpense() {
        return expense;
    }

    public void setExpense(Expense expense) {
        this.expense = expense;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public List<PurchaseOrderLine> getLines() {
        return lines;
    }
}

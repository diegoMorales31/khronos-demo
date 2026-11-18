package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.StockMovementType;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of a product's stock ledger: how much came in or went out, why, on which business day, at
 * what unit cost, and the product's stock right after. Movements are only ever added; a database
 * trigger rejects updates and deletes (see V13__products_and_stock_movements.sql). A mistake is
 * corrected with a new movement.
 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Always the product's establishment; kept here so listings and reports filter on it directly
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockMovementType type;

    // Signed: positive comes in, negative goes out
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    // The cost of one unit this movement is valued at; null when neither the purchase nor the product had one
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(length = 255)
    private String reason;

    // The business day the movement counts for
    @Column(nullable = false)
    private LocalDate date;

    // The product's stock right after this movement was recorded
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal stockAfter;

    // The purchase order this purchase was received with; an order with movements can't be deleted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private Instant createdAt;

    // Deleting the account that recorded the movement clears this link; the name below stays
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(nullable = false, length = 511)
    private String createdByName;

    public StockMovement() {
    }

    public StockMovement(Product product, StockMovementType type, BigDecimal quantity, BigDecimal unitCost,
                         String reason, LocalDate date, BigDecimal stockAfter, PurchaseOrder purchaseOrder,
                         User createdBy, Instant createdAt) {
        this.establishment = product.getEstablishment();
        this.purchaseOrder = purchaseOrder;
        this.product = product;
        this.type = type;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.reason = reason;
        this.date = date;
        this.stockAfter = stockAfter;
        this.createdBy = createdBy;
        this.createdByName = createdBy.displayName();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public Product getProduct() {
        return product;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getStockAfter() {
        return stockAfter;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
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
}

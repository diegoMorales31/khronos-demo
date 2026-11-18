package com.moga.khronosdemo.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One product of a purchase order: how much was ordered, at what cost, and how much came in once the
 * order is received.
 */
@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PurchaseOrder purchaseOrder;

    // A product on an order line can't be deleted (see V14)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Product product;

    // In the product's unit, more than 0
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    // Euros per unit; the one received replaces the one ordered when it is given, and a line that comes in
    // without either takes its product's cost at that moment
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    // Null until the order is received; 0 when the product didn't come
    @Column(precision = 12, scale = 3)
    private BigDecimal receivedQuantity;

    public PurchaseOrderLine() {
    }

    public PurchaseOrderLine(PurchaseOrder purchaseOrder, Product product, BigDecimal quantity, BigDecimal unitCost) {
        this.purchaseOrder = purchaseOrder;
        this.product = product;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    // The quantity received once the order is received, the quantity ordered before, times the unit cost,
    // in euros rounded to cents; null without a cost
    public BigDecimal lineTotal() {
        if (unitCost == null) {
            return null;
        }
        BigDecimal counted = receivedQuantity != null ? receivedQuantity : quantity;
        return counted.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(BigDecimal receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }
}

package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/**
 * Something an establishment keeps in stock: a bottle of rum, a keg of beer, a box of napkins. It has no
 * stock figure of its own: its stock is the sum of its stock movements (see StockMovement and
 * V13__products_and_stock_movements.sql).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Never changes after the product is created. A product with movements keeps its establishment from
    // being deleted (see V13)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Establishment establishment;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductCategory category;

    // Can't change once the product has movements, since every quantity recorded is in this unit
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductUnit unit;

    // At or below this stock the product is low; null means no minimum
    @Column(precision = 12, scale = 3)
    private BigDecimal minStock;

    // The last known cost of one unit, in euros; receiving a purchase with a cost updates it
    @Column(precision = 12, scale = 2)
    private BigDecimal unitCost;

    // The usual supplier. Deleting the supplier clears it
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Supplier supplier;

    // An inactive product takes no new movements or purchase order lines
    @Column(nullable = false)
    private boolean active = true;

    public Product() {
    }

    public Product(Establishment establishment) {
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

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public void setUnit(ProductUnit unit) {
        this.unit = unit;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

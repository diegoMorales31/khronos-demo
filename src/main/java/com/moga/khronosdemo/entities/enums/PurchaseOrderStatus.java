package com.moga.khronosdemo.entities.enums;

// Where a purchase order is: DRAFT -> ORDERED -> RECEIVED, or CANCELLED from DRAFT or ORDERED
public enum PurchaseOrderStatus {
    // Being prepared; the only status in which it can be edited or deleted
    DRAFT,
    // Sent to the supplier
    ORDERED,
    // Delivered: its stock movements are recorded
    RECEIVED,
    CANCELLED
}

package com.moga.khronosdemo.entities.enums;

import java.math.BigDecimal;

// Why a product's stock changed. The type sets the sign of the quantity
public enum StockMovementType {
    // Stock in, bought from a supplier
    PURCHASE,
    // Stock out that wasn't sold: breakage, out of date, given away, stolen
    WASTE,
    // Stock out used or sold, entered by hand
    CONSUMPTION,
    // A manual correction, in or out
    ADJUSTMENT,
    // The difference a physical count found; only a stock count records it
    COUNT;

    // The quantity as stored, from the one entered: a purchase adds, waste and consumption take away, and
    // an adjustment or a count keeps its own sign
    public BigDecimal signed(BigDecimal quantity) {
        return switch (this) {
            case PURCHASE -> quantity.abs();
            case WASTE, CONSUMPTION -> quantity.abs().negate();
            case ADJUSTMENT, COUNT -> quantity;
        };
    }
}

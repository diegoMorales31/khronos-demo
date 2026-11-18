package com.moga.khronosdemo.entities.enums;

// How an expense was paid. Only cash comes out of the till, so only cash expenses count in a cash closing
public enum PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    OTHER
}

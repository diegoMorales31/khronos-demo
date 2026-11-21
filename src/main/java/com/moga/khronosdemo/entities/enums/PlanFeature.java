package com.moga.khronosdemo.entities.enums;

// The parts of the app that only some plans include. Schedules, events, zones, the team, hourly
// rates and the hours worked are in every plan, so they have no feature here
public enum PlanFeature {
    // Cash closings, expenses and the business summary: what the day earned
    CASH,
    // Suppliers, products and their stock, stock counts and purchase orders
    INVENTORY
}

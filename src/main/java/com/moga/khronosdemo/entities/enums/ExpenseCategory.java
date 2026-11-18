package com.moga.khronosdemo.entities.enums;

// What an expense was for
public enum ExpenseCategory {
    // Food and drink stock
    SUPPLIES,
    RENT,
    // Electricity, water, gas and internet
    UTILITIES,
    // Wages and social security. The business summary leaves these out of the margin, which counts
    // staff cost from the hours worked instead, so pay is not counted twice
    PAYROLL,
    MAINTENANCE,
    MARKETING,
    TAXES,
    OTHER
}

package com.moga.khronosdemo.entities.enums;

// The staff cost a business summary's margin takes off
public enum LaborCostSource {
    // The cost of the hours worked, at each worker's rate: laborCost
    HOURS,
    // The PAYROLL expenses, because no worker's cost from the hours is known (laborCost is null)
    PAYROLL_EXPENSES
}

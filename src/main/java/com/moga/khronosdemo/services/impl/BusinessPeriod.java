package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.response.CategoryAmountResponseDTO;
import com.moga.khronosdemo.dtos.response.SummaryDayResponseDTO;
import com.moga.khronosdemo.dtos.response.SummaryPeriodResponseDTO;
import com.moga.khronosdemo.dtos.response.SummaryTotalsResponseDTO;
import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.entities.enums.DayClosingStatus;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.LaborCostSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The figures of the business summary for one period, from its cash closings, expenses and staff work.
 * Everything is added up in memory from what was loaded for the period, whatever its length.
 * <p>
 * The margin is sales - (expenses - PAYROLL expenses) - staff cost. The staff cost is the cost of the
 * hours worked when it is known, and the PAYROLL expenses are left out so pay is not counted twice. When
 * it is not known (no worker has a rate), the PAYROLL expenses stand in for it, so pay is still taken
 * off. The totals, each day and the previous period each decide this on their own figures.
 */
final class BusinessPeriod {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Comparator<CategoryAmountResponseDTO> LARGEST_FIRST = Comparator
            .comparing(CategoryAmountResponseDTO::amount, Comparator.reverseOrder())
            .thenComparing(CategoryAmountResponseDTO::category);

    private final LocalDate from;
    private final LocalDate to;
    private final LocalDate today;
    private final Map<LocalDate, CashClosing> closings = new TreeMap<>();
    private final Map<LocalDate, CashClosing.Figures> figures = new TreeMap<>();
    private final List<Expense> expenses;
    private final StaffWork staff;

    /**
     * @param closings and expenses may cover more days than the period: only those from 'from' to 'to' count
     * @param staff    the staff work of exactly this period
     * @param today    the establishment's local date, which decides which days without a closing are MISSING
     */
    BusinessPeriod(LocalDate from, LocalDate to, Collection<CashClosing> closings, Collection<Expense> expenses,
                   StaffWork staff, LocalDate today) {
        this.from = from;
        this.to = to;
        this.today = today;
        this.staff = staff;
        this.expenses = expenses.stream().filter(expense -> within(expense.getDate())).toList();
        Map<LocalDate, BigDecimal> cashExpenses = this.expenses.stream()
                .filter(Expense::isCash)
                .collect(Collectors.groupingBy(Expense::getDate, Collectors.reducing(ZERO, Expense::getAmount, BigDecimal::add)));
        closings.stream().filter(closing -> within(closing.getBusinessDate())).forEach(closing -> {
            this.closings.put(closing.getBusinessDate(), closing);
            // A draft's figures follow the day's cash expenses; a closed day keeps the ones it was closed with
            this.figures.put(closing.getBusinessDate(),
                    closing.figures(cashExpenses.getOrDefault(closing.getBusinessDate(), ZERO)));
        });
    }

    SummaryTotalsResponseDTO totals() {
        BigDecimal sales = sales();
        BigDecimal laborCost = staff.actualCost();
        int workersWithoutCost = staff.workersWithoutActualCost();
        long closed = closings.values().stream().filter(CashClosing::isClosed).count();
        Set<LocalDate> activeDays = staff.activeDays();
        long missing = from.datesUntil(to.plusDays(1)).filter(day -> isMissing(day, activeDays)).count();
        return new SummaryTotalsResponseDTO(
                sales,
                sumOfClosings(CashClosing::getCashSales),
                sumOfClosings(CashClosing::getCardSales),
                sumOfClosings(CashClosing::getOtherSales),
                sumOfClosings(closing -> closing.getTips() != null ? closing.getTips() : ZERO),
                sum(expenses),
                sum(payroll(expenses)),
                laborCost,
                laborCost != null && workersWithoutCost > 0,
                workersWithoutCost,
                staff.plannedCost(),
                margin(sales, expenses, laborCost),
                laborCostSource(laborCost),
                laborCostPercent(laborCost, sales),
                cashDifference(true),
                cashDifference(false),
                (int) closed,
                closings.size() - (int) closed,
                (int) missing);
    }

    // Every day of the period, earliest first, with or without anything on it
    List<SummaryDayResponseDTO> days() {
        Map<LocalDate, List<Expense>> expensesByDay = expenses.stream().collect(Collectors.groupingBy(Expense::getDate));
        Map<LocalDate, BigDecimal> laborCostByDay = staff.actualCostByDay();
        Set<LocalDate> activeDays = staff.activeDays();
        List<SummaryDayResponseDTO> days = new ArrayList<>();
        from.datesUntil(to.plusDays(1)).forEach(day -> {
            List<Expense> dayExpenses = expensesByDay.getOrDefault(day, List.of());
            CashClosing.Figures dayFigures = figures.get(day);
            BigDecimal sales = dayFigures != null ? dayFigures.totalSales() : ZERO;
            BigDecimal laborCost = laborCostByDay != null ? laborCostByDay.getOrDefault(day, ZERO) : null;
            days.add(new SummaryDayResponseDTO(
                    day,
                    sales,
                    sum(dayExpenses),
                    laborCost,
                    margin(sales, dayExpenses, laborCost),
                    closingStatus(day, activeDays),
                    dayFigures != null ? dayFigures.difference() : null));
        });
        return days;
    }

    // Only the categories with expenses, the largest amount first
    List<CategoryAmountResponseDTO> expensesByCategory() {
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        expenses.forEach(expense -> byCategory.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add));
        return byCategory.entrySet().stream()
                .filter(category -> category.getValue().signum() > 0)
                .map(category -> new CategoryAmountResponseDTO(category.getKey(), category.getValue()))
                .sorted(LARGEST_FIRST)
                .toList();
    }

    SummaryPeriodResponseDTO asPrevious() {
        BigDecimal sales = sales();
        BigDecimal laborCost = staff.actualCost();
        return new SummaryPeriodResponseDTO(from, to, sales, sum(expenses), laborCost,
                margin(sales, expenses, laborCost), laborCostSource(laborCost));
    }

    private BigDecimal sales() {
        return figures.values().stream().map(CashClosing.Figures::totalSales).reduce(ZERO, BigDecimal::add);
    }

    private DayClosingStatus closingStatus(LocalDate day, Set<LocalDate> activeDays) {
        CashClosing closing = closings.get(day);
        if (closing != null) {
            return closing.isClosed() ? DayClosingStatus.CLOSED : DayClosingStatus.DRAFT;
        }
        return isMissing(day, activeDays) ? DayClosingStatus.MISSING : DayClosingStatus.NONE;
    }

    // A closing is only due once the day is over, and only if someone was scheduled or clocked in
    private boolean isMissing(LocalDate day, Set<LocalDate> activeDays) {
        return !closings.containsKey(day) && day.isBefore(today) && activeDays.contains(day);
    }

    // The differences of the closed days, or of the drafts, added up. A draft whose cash has not been
    // counted has no difference yet and adds nothing
    private BigDecimal cashDifference(boolean closed) {
        return closings.values().stream()
                .filter(closing -> closing.isClosed() == closed)
                .map(closing -> figures.get(closing.getBusinessDate()).difference())
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal sumOfClosings(Function<CashClosing, BigDecimal> amount) {
        return closings.values().stream().map(amount).reduce(ZERO, BigDecimal::add);
    }

    private boolean within(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    // sales - (expenses - payroll) - staff cost, where the staff cost is laborCost or, when that is unknown,
    // the PAYROLL expenses among the given ones
    private static BigDecimal margin(BigDecimal sales, List<Expense> expenses, BigDecimal laborCost) {
        BigDecimal payrollExpenses = sum(payroll(expenses));
        BigDecimal staffCost = laborCost != null ? laborCost : payrollExpenses;
        return sales.subtract(sum(expenses).subtract(payrollExpenses)).subtract(staffCost);
    }

    private static LaborCostSource laborCostSource(BigDecimal laborCost) {
        return laborCost != null ? LaborCostSource.HOURS : LaborCostSource.PAYROLL_EXPENSES;
    }

    // One decimal; null when there are no sales to divide by or the staff cost is unknown
    private static BigDecimal laborCostPercent(BigDecimal laborCost, BigDecimal sales) {
        if (laborCost == null || sales.signum() == 0) {
            return null;
        }
        return laborCost.multiply(HUNDRED).divide(sales, 1, RoundingMode.HALF_UP);
    }

    private static List<Expense> payroll(List<Expense> expenses) {
        return expenses.stream().filter(expense -> expense.getCategory() == ExpenseCategory.PAYROLL).toList();
    }

    private static BigDecimal sum(List<Expense> expenses) {
        return expenses.stream().map(Expense::getAmount).reduce(ZERO, BigDecimal::add);
    }
}

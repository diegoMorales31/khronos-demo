package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.CashClosingStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static com.moga.khronosdemo.entities.Money.euros;

/**
 * The cash closing of one business day at an establishment: the takings by payment method, the cash
 * float, cash taken out of the till and the cash counted at the end, all entered by hand in euros. It
 * is an internal management record, not an invoice or a tax record.
 * <p>
 * A draft can be edited, and its figures follow the cash expenses recorded for its day. It can be saved
 * before the cash is counted: countedCash, and the difference worked out from it, stay null until then.
 * Closing it needs the counted cash and stores the figures, which stay as they were until it is
 * reopened. Every change is kept in {@link CashClosingChange}.
 */
@Entity
@Table(
        name = "cash_closings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cash_closings_establishment_business_date",
                        columnNames = {"establishment_id", "business_date"})
        }
)
public class CashClosing {

    /**
     * What the API works out from the amounts entered, in euros with two decimals: the day's cash
     * expenses, total sales (cash + card + other), the cash expected in the till (float + cash sales -
     * cash expenses - withdrawals) and the difference (counted - expected), which is negative when cash
     * is missing and null while a draft's cash has not been counted.
     */
    public record Figures(BigDecimal cashExpenses, BigDecimal totalSales, BigDecimal expectedCash,
                          BigDecimal difference) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // The database refuses to delete an establishment that has closings (see V10), and so does the service
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Establishment establishment;

    // The day of service closed: a night that ends at 02:00 belongs to the day before
    @Column(nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CashClosingStatus status = CashClosingStatus.DRAFT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal openingFloat;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cardSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal otherSales;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashWithdrawals;

    // Null while a draft's cash has not been counted; a closed day always has it (see V11)
    @Column(precision = 12, scale = 2)
    private BigDecimal countedCash;

    // Informative only: tips are not sales
    @Column(precision = 12, scale = 2)
    private BigDecimal tips;

    @Column(length = 500)
    private String notes;

    // The figures the day was closed with; null while it is a draft
    @Column(precision = 12, scale = 2)
    private BigDecimal cashExpenses;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalSales;

    @Column(precision = 12, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(length = 255)
    private String differenceReason;

    private Instant closedAt;

    // Deleting the account that closed the day clears this link; the name below stays
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User closedBy;

    @Column(length = 511)
    private String closedByName;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public CashClosing() {
    }

    public CashClosing(Establishment establishment, LocalDate businessDate, Instant createdAt) {
        this.establishment = establishment;
        this.businessDate = businessDate;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isClosed() {
        return status == CashClosingStatus.CLOSED;
    }

    public boolean isCounted() {
        return countedCash != null;
    }

    /**
     * The figures the day was closed with or, for a draft, the ones that follow from the amounts entered
     * and the cash expenses recorded for the day so far. A closed day ignores the argument, which may
     * then be null.
     */
    public Figures figures(BigDecimal cashExpensesSoFar) {
        if (isClosed()) {
            return new Figures(cashExpenses, totalSales, expectedCash, difference);
        }
        BigDecimal expected = openingFloat.add(cashSales).subtract(cashExpensesSoFar).subtract(cashWithdrawals);
        return new Figures(
                euros(cashExpensesSoFar),
                euros(cashSales.add(cardSales).add(otherSales)),
                euros(expected),
                countedCash != null ? euros(countedCash.subtract(expected)) : null);
    }

    // Freezes the figures worked out at closing time
    public void close(Figures figures, String differenceReason, User closedBy, Instant closedAt) {
        this.status = CashClosingStatus.CLOSED;
        this.cashExpenses = figures.cashExpenses();
        this.totalSales = figures.totalSales();
        this.expectedCash = figures.expectedCash();
        this.difference = figures.difference();
        this.differenceReason = differenceReason;
        this.closedBy = closedBy;
        this.closedByName = closedBy.displayName();
        this.closedAt = closedAt;
        this.updatedAt = closedAt;
    }

    // Back to a draft: the stored figures, the reason for the difference and who closed it are cleared,
    // and stay in the history
    public void reopen(Instant reopenedAt) {
        this.status = CashClosingStatus.DRAFT;
        this.cashExpenses = null;
        this.totalSales = null;
        this.expectedCash = null;
        this.difference = null;
        this.differenceReason = null;
        this.closedBy = null;
        this.closedByName = null;
        this.closedAt = null;
        this.updatedAt = reopenedAt;
    }

    public Long getId() {
        return id;
    }

    public Establishment getEstablishment() {
        return establishment;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public CashClosingStatus getStatus() {
        return status;
    }

    public BigDecimal getOpeningFloat() {
        return openingFloat;
    }

    public void setOpeningFloat(BigDecimal openingFloat) {
        this.openingFloat = openingFloat;
    }

    public BigDecimal getCashSales() {
        return cashSales;
    }

    public void setCashSales(BigDecimal cashSales) {
        this.cashSales = cashSales;
    }

    public BigDecimal getCardSales() {
        return cardSales;
    }

    public void setCardSales(BigDecimal cardSales) {
        this.cardSales = cardSales;
    }

    public BigDecimal getOtherSales() {
        return otherSales;
    }

    public void setOtherSales(BigDecimal otherSales) {
        this.otherSales = otherSales;
    }

    public BigDecimal getCashWithdrawals() {
        return cashWithdrawals;
    }

    public void setCashWithdrawals(BigDecimal cashWithdrawals) {
        this.cashWithdrawals = cashWithdrawals;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public void setCountedCash(BigDecimal countedCash) {
        this.countedCash = countedCash;
    }

    public BigDecimal getTips() {
        return tips;
    }

    public void setTips(BigDecimal tips) {
        this.tips = tips;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDifferenceReason() {
        return differenceReason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public User getClosedBy() {
        return closedBy;
    }

    public String getClosedByName() {
        return closedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

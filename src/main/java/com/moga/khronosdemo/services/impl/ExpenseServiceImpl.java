package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.CashClosingStatus;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.ExpenseMapper;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.ExpenseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Expenses entered by hand. Cash expenses come out of the till, so they lower the cash a closing
 * expects: once a day's cash closing is closed, its cash expenses can't be added, changed or deleted
 * until it is reopened. Every change to an expense takes its lock through CashRecordLock, as cash
 * closings do: the establishment's row first, and only then the expense, so an expense and the closing
 * of its day take turns.
 * <p>
 * An expense created by receiving a purchase order (purchase_orders.expense_id) is that order's record of
 * what was paid, so it can't be edited: it answers 409 EXPENSE_FROM_PURCHASE_ORDER. It can be deleted, and
 * entered again by hand if it needs correcting; the order then keeps its movements and loses the link.
 * Receiving links it under the establishment's lock, which an edit takes first too.
 */
@Service
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CashClosingRepository cashClosingRepository;
    private final EstablishmentRepository establishmentRepository;
    private final UserRepository userRepository;
    private final CashRecordLock cashRecordLock;
    private final ExpenseMapper expenseMapper;
    private final Clock clock;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository, PurchaseOrderRepository purchaseOrderRepository,
                              CashClosingRepository cashClosingRepository,
                              EstablishmentRepository establishmentRepository, UserRepository userRepository,
                              CashRecordLock cashRecordLock, ExpenseMapper expenseMapper, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.establishmentRepository = establishmentRepository;
        this.userRepository = userRepository;
        this.cashRecordLock = cashRecordLock;
        this.expenseMapper = expenseMapper;
        this.clock = clock;
    }

    // Newest first, then by id, newest first too, over at most 366 days (see DateRange.capped): without
    // dates, the last 366 up to today. Two queries however many expenses: the expenses, and the orders
    // whose receipt created any of them
    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                       ExpenseCategory category) {
        DateRange range = DateRange.capped(from, to, ReportServiceImpl.MAX_DAYS, () -> today(establishmentId));
        List<Expense> expenses = expenseRepository.findByEstablishment(establishmentId, range.from(), range.to(),
                category);
        if (expenses.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> purchaseOrders = purchaseOrderRepository
                .findExpenseOrders(establishmentId, range.from(), range.to()).stream()
                .collect(Collectors.toMap(PurchaseOrderRepository.ExpenseOrder::getExpenseId,
                        PurchaseOrderRepository.ExpenseOrder::getPurchaseOrderId));
        return expenses.stream()
                .map(expense -> expenseMapper.toResponse(expense, purchaseOrders.get(expense.getId())))
                .toList();
    }

    @Override
    public ExpenseResponseDTO create(ExpenseRequestDTO request, Long actorId) {
        Establishment establishment = cashRecordLock.lockEstablishment(request.establishmentId());
        checkNotInFuture(establishment, request.date());
        if (request.paymentMethod() == PaymentMethod.CASH) {
            checkCashDayOpen(establishment.getId(), request.date());
        }

        User author = getUser(actorId);
        Expense expense = new Expense(establishment, author, author.displayName(), auditNow());
        expenseMapper.updateEntity(expense, request);
        // A new expense has no order yet: receiving one links it after creating it
        return expenseMapper.toResponse(expenseRepository.save(expense), null);
    }

    // The establishment never changes: the one in the request is ignored. An expense created by receiving a
    // purchase order can't be edited. A cash expense can't leave a closed day, and an expense can't become a
    // cash expense of a closed day
    @Override
    public ExpenseResponseDTO update(Long id, ExpenseRequestDTO request) {
        Expense expense = cashRecordLock.lockExpense(id);
        checkNotFromPurchaseOrder(id);
        Long establishmentId = expense.getEstablishment().getId();
        if (expense.isCash()) {
            checkCashDayOpen(establishmentId, expense.getDate());
        }
        if (request.paymentMethod() == PaymentMethod.CASH) {
            checkCashDayOpen(establishmentId, request.date());
        }
        checkNotInFuture(expense.getEstablishment(), request.date());
        expenseMapper.updateEntity(expense, request);
        return expenseMapper.toResponse(expense, null);
    }

    @Override
    public void delete(Long id) {
        Expense expense = cashRecordLock.lockExpense(id);
        if (expense.isCash()) {
            checkCashDayOpen(expense.getEstablishment().getId(), expense.getDate());
        }
        expenseRepository.delete(expense);
    }

    // Its amount, day and category are the order's record of what was paid: correcting it means deleting it
    // and entering it again
    private void checkNotFromPurchaseOrder(Long expenseId) {
        Optional<Long> purchaseOrderId = purchaseOrderRepository.findIdByExpenseId(expenseId);
        if (purchaseOrderId.isPresent()) {
            throw new BusinessRuleException("This expense was created when purchase order #" + purchaseOrderId.get()
                    + " was received and cannot be edited; to correct it, delete it and enter it again",
                    ErrorCode.EXPENSE_FROM_PURCHASE_ORDER, Map.of("purchaseOrderId", purchaseOrderId.get().toString()));
        }
    }

    private void checkCashDayOpen(Long establishmentId, LocalDate date) {
        if (cashClosingRepository.existsByEstablishment_IdAndBusinessDateAndStatus(establishmentId, date,
                CashClosingStatus.CLOSED)) {
            throw new BusinessRuleException("The cash closing of " + date + " is closed; reopen it to add, change"
                    + " or delete cash expenses of that day", ErrorCode.CASH_DAY_CLOSED, Map.of("date", date.toString()));
        }
    }

    // "Today" is the establishment's local date, so a venue in the Canaries can't enter tomorrow's expenses
    private void checkNotInFuture(Establishment establishment, LocalDate date) {
        if (date.isAfter(today(establishment))) {
            throw new BusinessRuleException("The expense date cannot be in the future", ErrorCode.FUTURE_DATE);
        }
    }

    private LocalDate today(Establishment establishment) {
        return LocalDate.ofInstant(clock.instant(), establishment.getZoneId());
    }

    private LocalDate today(Long establishmentId) {
        return today(establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId)));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // Audit times keep the precision PostgreSQL stores, so a response matches what is read back later
    private Instant auditNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * How every change to an establishment's cash closings and expenses takes its lock, in one place so both
 * services lock in the same way: the establishment's row first (EstablishmentRepository.lockById), and
 * only then the closing or expense, read after the lock so the checks that follow see whatever an earlier
 * request committed. That is what keeps two closings of one day from both being created, and a cash
 * expense from slipping into a day while it is being closed. Receiving a purchase order, which may create
 * an expense, takes the establishment's lock here too, before the order's and its products' (see
 * StockLedger). It runs inside the caller's transaction, which holds the lock until it ends.
 */
@Component
class CashRecordLock {

    private final EstablishmentRepository establishmentRepository;
    private final CashClosingRepository cashClosingRepository;
    private final ExpenseRepository expenseRepository;

    CashRecordLock(EstablishmentRepository establishmentRepository, CashClosingRepository cashClosingRepository,
                   ExpenseRepository expenseRepository) {
        this.establishmentRepository = establishmentRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.expenseRepository = expenseRepository;
    }

    Establishment lockEstablishment(Long establishmentId) {
        return establishmentRepository.lockById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId));
    }

    CashClosing lockCashClosing(Long id) {
        return lockAndGet(id, "CashClosing", cashClosingRepository::findEstablishmentIdById, cashClosingRepository::findById);
    }

    Expense lockExpense(Long id) {
        return lockAndGet(id, "Expense", expenseRepository::findEstablishmentIdById, expenseRepository::findById);
    }

    // Reads only the record's establishment id, locks that establishment and then loads the record
    private <T> T lockAndGet(Long id, String resource, Function<Long, Optional<Long>> establishmentIdOf,
                             Function<Long, Optional<T>> load) {
        Long establishmentId = establishmentIdOf.apply(id)
                .orElseThrow(() -> new ResourceNotFoundException(resource, id));
        lockEstablishment(establishmentId);
        return load.apply(id)
                .orElseThrow(() -> new ResourceNotFoundException(resource, id));
    }
}

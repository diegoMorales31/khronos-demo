package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.request.ExpenseRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requests on the same establishment's cash take turns: two closings of the same day can't both be
 * saved, and a cash expense can't slip into a day while it is being closed. The unique constraint still
 * answers 409 if a closing was inserted without the lock.
 */
class CashClosingLockingTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private CashClosingService cashClosingService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    private User owner;
    private Establishment venue;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T21:00:00Z"));
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseFirst.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void twoPostsForTheSameDaySentTogetherCreateOneClosing() throws Exception {
        RequestPostProcessor caller = as(owner);
        String body = """
                { "establishmentId": %d, "businessDate": "2030-05-10", "openingFloat": 100, "cashSales": 10,
                  "cardSales": 0, "countedCash": 110 }
                """.formatted(venue.getId());
        CyclicBarrier start = new CyclicBarrier(2);
        List<Future<Integer>> posts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            posts.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(postJson("/api/cash-closings", body).with(caller))
                        .andReturn().getResponse().getStatus();
            }));
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> post : posts) {
            statuses.add(post.get(10, TimeUnit.SECONDS));
        }
        statuses.sort(null);
        assertEquals(List.of(201, 409), statuses);
        assertEquals(1, cashClosingRepository.count());
        assertEquals(1, cashClosingChangeRepository.count());
    }

    @Test
    void aSecondClosingOfTheDayWaitsForTheFirstAndThenFails() throws Exception {
        holdOpen(() -> cashClosingService.create(closing(), owner.getId()));

        Future<?> second = pool.submit(() -> cashClosingService.create(closing(), owner.getId()));
        assertWaits(second);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().startsWith("A cash closing already exists for 2030-05-10"));
        assertEquals(1, cashClosingRepository.count());
    }

    @Test
    void aCashExpenseWaitsWhileItsDayIsBeingClosedAndThenFails() throws Exception {
        Long id = cashClosingService.create(closing(), owner.getId()).id();
        holdOpen(() -> cashClosingService.close(id, null, owner.getId()));

        Future<?> expense = pool.submit(() -> expenseService.create(cashExpense("4.50"), owner.getId()));
        assertWaits(expense);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> expense.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals(0, expenseRepository.count());
        CashClosing closed = cashClosingRepository.findById(id).orElseThrow();
        assertTrue(closed.isClosed());
        assertEquals(new BigDecimal("0.00"), closed.figures(null).cashExpenses());
    }

    @Test
    void closingADayWaitsForACashExpenseBeingAddedAndCountsIt() throws Exception {
        Long id = cashClosingService.create(closing(), owner.getId()).id();
        holdOpen(() -> expenseService.create(cashExpense("4.50"), owner.getId()));

        Future<CashClosingResponseDTO> close = pool.submit(() -> cashClosingService.close(id, null, owner.getId()));
        assertWaits(close);

        releaseFirst.countDown();
        CashClosingResponseDTO closed = close.get(10, TimeUnit.SECONDS);
        assertEquals(new BigDecimal("4.50"), closed.cashExpenses());
        assertEquals(new BigDecimal("105.50"), closed.expectedCash());
        assertEquals(new BigDecimal("4.50"), closed.difference());
    }

    @Test
    void theUniqueConstraintAnswersAConflictForAClosingInsertedWithoutTheLock() throws Exception {
        // A closing written straight to the database, as a buggy path or a manual fix might, without the lock
        holdOpen(() -> jdbcTemplate.update("""
                insert into cash_closings (id, establishment_id, business_date, status, opening_float, cash_sales,
                    card_sales, other_sales, cash_withdrawals, counted_cash, created_at, updated_at)
                values (-1, ?, ?, 'DRAFT', 0, 0, 0, 0, 0, 0, now(), now())
                """, venue.getId(), DAY));

        // The service doesn't see the uncommitted row, so its insert waits on the unique index
        Future<?> create = pool.submit(() -> cashClosingService.create(closing(), owner.getId()));
        assertWaits(create);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> create.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals("A cash closing already exists for 2030-05-10 at this establishment; edit that one instead",
                failure.getCause().getMessage());
        assertEquals(ErrorCode.CASH_CLOSING_EXISTS, ((BusinessRuleException) failure.getCause()).getCode());
        assertEquals(Map.of("date", "2030-05-10"), ((BusinessRuleException) failure.getCause()).getDetails());
        assertEquals(1, cashClosingRepository.count());

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into cash_closings (id, establishment_id, business_date, status, opening_float, cash_sales,
                    card_sales, other_sales, cash_withdrawals, counted_cash, created_at, updated_at)
                values (-2, ?, ?, 'DRAFT', 0, 0, 0, 0, 0, 0, now(), now())
                """, venue.getId(), DAY));
    }

    // Runs the work in a transaction on another thread and keeps that transaction open until releaseFirst
    private void holdOpen(Callable<?> work) throws InterruptedException {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        pool.submit(() -> transaction.executeWithoutResult(status -> {
            try {
                work.call();
                firstDone.countDown();
                releaseFirst.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }));
        assertTrue(firstDone.await(10, TimeUnit.SECONDS), "the first request did not finish");
    }

    private static void assertWaits(Future<?> request) throws InterruptedException {
        Thread.sleep(500);
        assertFalse(request.isDone(), "the second request should wait until the first one commits");
    }

    private CashClosingRequestDTO closing() {
        return new CashClosingRequestDTO(venue.getId(), DAY, new BigDecimal("100"), new BigDecimal("10"),
                BigDecimal.ZERO, null, null, new BigDecimal("110"), null, null);
    }

    private ExpenseRequestDTO cashExpense(String amount) {
        return new ExpenseRequestDTO(venue.getId(), DAY, ExpenseCategory.SUPPLIES, "Lemons", null,
                new BigDecimal(amount), PaymentMethod.CASH, null);
    }
}

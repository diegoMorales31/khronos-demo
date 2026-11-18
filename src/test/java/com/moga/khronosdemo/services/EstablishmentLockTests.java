package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ClockInRequestDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The establishment's row lock that every change to its cash closings and expenses takes first
 * (EstablishmentRepository.lockById): the statement PostgreSQL receives for it, and that rows referencing
 * the establishment, such as a worker's clock-in, can still be inserted while it is held. That cash
 * changes still wait for each other is covered by CashClosingLockingTests.
 */
class EstablishmentLockTests extends IntegrationTest {

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch locked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private Establishment venue;
    private Worker worker;

    @BeforeEach
    void setUp() {
        venue = createEstablishment(createOwner("owner"));
        worker = createWorker(venue, "worker");
        clock.setNow(Instant.parse("2030-05-10T20:00:00Z"));
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        release.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void locksTheRowForNoKeyUpdate() throws Exception {
        holdLock();

        // The last statement of the connection that holds the lock, as PostgreSQL received it
        List<String> statements = jdbcTemplate.queryForList("""
                select query from pg_stat_activity
                where datname = current_database() and state = 'idle in transaction' and query ilike '%establishments%'
                """, String.class);
        assertEquals(1, statements.size(), statements.toString());
        String sql = statements.get(0).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        // Hibernate 7.4 ends it with "for no key update of <alias>"
        assertTrue(sql.contains(" for no key update"), sql);
        assertFalse(sql.contains(" for update"), sql);
    }

    @Test
    void aWorkerCanClockInWhileTheEstablishmentIsLocked() throws Exception {
        holdLock();

        // Inserting the entry checks its foreign key to the establishment with FOR KEY SHARE, which
        // FOR NO KEY UPDATE lets through
        Future<TimeEntryResponseDTO> clockIn =
                pool.submit(() -> timeEntryService.clockIn(worker.getUserId(), new ClockInRequestDTO(null)));
        TimeEntryResponseDTO entry = clockIn.get(5, TimeUnit.SECONDS);
        assertNotNull(entry.id());
        assertEquals(1, timeEntryRepository.count());
    }

    // Takes the lock in a transaction on another thread and keeps it until the test ends
    private void holdLock() throws InterruptedException {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        pool.submit(() -> transaction.executeWithoutResult(status -> {
            establishmentRepository.lockById(venue.getId()).orElseThrow();
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(locked.await(10, TimeUnit.SECONDS), "the lock was not taken");
    }
}

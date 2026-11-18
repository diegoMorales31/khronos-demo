package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.request.StockCountItemRequestDTO;
import com.moga.khronosdemo.dtos.request.StockCountRequestDTO;
import com.moga.khronosdemo.dtos.request.StockMovementRequestDTO;
import com.moga.khronosdemo.dtos.response.StockMovementResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Movements of the same product take turns, so each one's stockAfter starts from what the one before it
 * left, and the ledger can't be changed or emptied behind the API's back: the database rejects updates
 * and deletes of movements.
 */
class StockMovementLockingTests extends IntegrationTest {

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockCountService stockCountService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    private User owner;
    private Establishment venue;
    private Long rum;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        rum = productService.create(new ProductRequestDTO(venue.getId(), "Ron", ProductCategory.SPIRITS,
                ProductUnit.BOTTLE, null, new BigDecimal("10"), null)).id();
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseFirst.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void aSecondMovementWaitsForTheFirstAndStartsFromTheStockItLeft() throws Exception {
        holdOpen(() -> stockMovementService.create(movement(StockMovementType.PURCHASE, "6"), owner.getId()));

        Future<StockMovementResponseDTO> second = pool.submit(() ->
                stockMovementService.create(movement(StockMovementType.CONSUMPTION, "1"), owner.getId()));
        assertWaits(second);

        releaseFirst.countDown();
        assertEquals(new BigDecimal("5.000"), second.get(10, TimeUnit.SECONDS).stockAfter());
    }

    @Test
    void movementsSentTogetherEachGetTheirOwnStockAfter() throws Exception {
        RequestPostProcessor caller = as(owner);
        int threads = 4;
        int each = 5;
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Future<List<Integer>>> requests = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            requests.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                List<Integer> statuses = new ArrayList<>();
                for (int i = 0; i < each; i++) {
                    statuses.add(mockMvc.perform(postJson("/api/stock-movements", """
                                    { "productId": %d, "type": "PURCHASE", "quantity": 1 }
                                    """.formatted(rum)).with(caller))
                            .andReturn().getResponse().getStatus());
                }
                return statuses;
            }));
        }
        for (Future<List<Integer>> request : requests) {
            assertEquals(List.of(201, 201, 201, 201, 201), request.get(30, TimeUnit.SECONDS));
        }

        // Every movement saw the one before it: the stock after them is 1, 2, ... 20, with no repeats
        List<BigDecimal> stockAfter = jdbcTemplate.queryForList(
                "select stock_after from stock_movements where product_id = ? order by stock_after", BigDecimal.class, rum);
        List<BigDecimal> expected = new ArrayList<>();
        for (int i = 1; i <= threads * each; i++) {
            expected.add(new BigDecimal(i).setScale(3));
        }
        assertEquals(expected, stockAfter);
        assertEquals(new BigDecimal("20.000"), productService.findById(rum).currentStock());
    }

    @Test
    void aCountWaitsForAMovementBeingRecordedAndCountsAgainstTheStockItLeft() throws Exception {
        holdOpen(() -> stockMovementService.create(movement(StockMovementType.PURCHASE, "6"), owner.getId()));

        Future<List<StockMovementResponseDTO>> count = pool.submit(() -> stockCountService.count(
                new StockCountRequestDTO(venue.getId(), null, null,
                        List.of(new StockCountItemRequestDTO(rum, new BigDecimal("5")))), owner.getId()));
        assertWaits(count);

        releaseFirst.countDown();
        StockMovementResponseDTO counted = count.get(10, TimeUnit.SECONDS).get(0);
        assertEquals(new BigDecimal("-1.000"), counted.quantity());
        assertEquals(new BigDecimal("5.000"), counted.stockAfter());
    }

    @Test
    void deactivatingAProductWaitsForAMovementBeingRecordedAndThenStopsTheNextOne() throws Exception {
        holdOpen(() -> stockMovementService.create(movement(StockMovementType.PURCHASE, "2"), owner.getId()));

        Future<?> deactivate = pool.submit(() -> productService.setActive(rum, false));
        assertWaits(deactivate);
        releaseFirst.countDown();
        deactivate.get(10, TimeUnit.SECONDS);

        assertThrows(BusinessRuleException.class,
                () -> stockMovementService.create(movement(StockMovementType.CONSUMPTION, "1"), owner.getId()));
        assertEquals(1, stockMovementRepository.count());
    }

    @Test
    void deletingAProductWaitsForAMovementBeingRecordedAndThenFails() throws Exception {
        holdOpen(() -> stockMovementService.create(movement(StockMovementType.PURCHASE, "2"), owner.getId()));

        Future<?> delete = pool.submit(() -> productService.delete(rum));
        assertWaits(delete);
        releaseFirst.countDown();

        ExecutionException failure = assertThrows(ExecutionException.class, () -> delete.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertTrue(productRepository.existsById(rum));
    }

    @Test
    void theDatabaseRejectsChangingOrDeletingAMovement() {
        Long id = stockMovementService.create(movement(StockMovementType.PURCHASE, "2"), owner.getId()).id();

        for (String sql : List.of(
                "update stock_movements set quantity = 200 where id = ?",
                "update stock_movements set stock_after = 0 where id = ?",
                "update stock_movements set reason = 'edited' where id = ?",
                "update stock_movements set created_by_name = 'Someone else' where id = ?",
                "update stock_movements set created_by = null, quantity = 3 where id = ?")) {
            DataAccessException rejected = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(sql, id), sql);
            assertTrue(rejected.getMostSpecificCause().getMessage().contains("stock_movements rows cannot be modified"), sql);
        }
        DataAccessException deleted = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from stock_movements where id = ?", id));
        assertTrue(deleted.getMostSpecificCause().getMessage().contains("stock_movements rows cannot be deleted"));
        assertEquals(new BigDecimal("2.000"), stockMovementRepository.findById(id).orElseThrow().getQuantity());

        // The one change allowed: the database clearing the author when their account is deleted
        assertEquals(1, jdbcTemplate.update("update stock_movements set created_by = null where id = ?", id));
        assertNull(jdbcTemplate.queryForObject("select created_by from stock_movements where id = ?", Long.class, id));
        assertEquals("First owner Last owner", stockMovementRepository.findById(id).orElseThrow().getCreatedByName());
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

    private StockMovementRequestDTO movement(StockMovementType type, String quantity) {
        return new StockMovementRequestDTO(rum, type, new BigDecimal(quantity), null, null, null);
    }
}

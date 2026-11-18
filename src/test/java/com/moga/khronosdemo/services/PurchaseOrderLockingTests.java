package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.request.ProductRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderLineRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveLineRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderReceiveRequestDTO;
import com.moga.khronosdemo.dtos.request.PurchaseOrderRequestDTO;
import com.moga.khronosdemo.dtos.request.StockMovementRequestDTO;
import com.moga.khronosdemo.dtos.request.SupplierRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.dtos.response.PurchaseOrderResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.entities.enums.ProductCategory;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
 * Receiving an order takes the establishment's lock, then the order's, then its products', the same
 * order every other request uses: two receipts of one order take turns, a cash expense can't slip into
 * a day while it is being closed, and a receipt, a movement and a cash closing sent together all finish.
 * Deleting a supplier or an establishment while an order or a movement that keeps it is being recorded
 * waits for it and then answers the delete's own 409.
 */
class PurchaseOrderLockingTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockMovementService stockMovementService;

    @Autowired
    private CashClosingService cashClosingService;

    @Autowired
    private EstablishmentService establishmentService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    private User owner;
    private Establishment venue;
    private Long beer;
    private Long orderId;
    private Long lineId;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T21:00:00Z"));
        Long supplier = supplierService.create(new SupplierRequestDTO(venue.getId(), "Mahou", null, null, null)).id();
        beer = productService.create(new ProductRequestDTO(venue.getId(), "Barril", ProductCategory.BEER,
                ProductUnit.KEG, null, new BigDecimal("90"), null)).id();
        PurchaseOrderResponseDTO order = purchaseOrderService.create(new PurchaseOrderRequestDTO(venue.getId(), supplier,
                null, null, List.of(new PurchaseOrderLineRequestDTO(beer, new BigDecimal("2"), new BigDecimal("90")))),
                owner.getId());
        orderId = order.id();
        lineId = order.lines().get(0).id();
        purchaseOrderService.order(orderId);
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseFirst.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void aSecondReceiptOfTheSameOrderWaitsForTheFirstAndThenFails() throws Exception {
        holdOpen(() -> purchaseOrderService.receive(orderId, receipt(false, null), owner.getId()));

        Future<?> second = pool.submit(() -> purchaseOrderService.receive(orderId, receipt(false, null), owner.getId()));
        assertWaits(second);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
        assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals("Only an ordered purchase order can be received; this one is RECEIVED", failure.getCause().getMessage());
        assertEquals(1, stockMovementRepository.count());
    }

    @Test
    void aReceiptWithACashExpenseWaitsWhileItsDayIsBeingClosedAndThenReceivesNothing() throws Exception {
        Long closing = cashClosingService.create(closing(), owner.getId()).id();
        holdOpen(() -> cashClosingService.close(closing, null, owner.getId()));

        Future<?> receipt = pool.submit(() -> purchaseOrderService.receive(orderId, receipt(true, PaymentMethod.CASH),
                owner.getId()));
        assertWaits(receipt);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> receipt.get(10, TimeUnit.SECONDS));
        BusinessRuleException refused = assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals("The cash closing of 2030-05-10 is closed; reopen it to add, change or delete cash expenses of that day",
                refused.getMessage());
        assertEquals(ErrorCode.CASH_DAY_CLOSED, refused.getCode());
        assertEquals(Map.of("date", "2030-05-10"), refused.getDetails());
        assertEquals(0, stockMovementRepository.count());
        assertEquals(0, expenseRepository.count());
        assertEquals(PurchaseOrderStatus.ORDERED, purchaseOrderService.findById(orderId).status());
    }

    @Test
    void closingTheDayWaitsForAReceiptWithACashExpenseAndCountsIt() throws Exception {
        Long closing = cashClosingService.create(closing(), owner.getId()).id();
        holdOpen(() -> purchaseOrderService.receive(orderId, receipt(true, PaymentMethod.CASH), owner.getId()));

        Future<CashClosingResponseDTO> close = pool.submit(() -> cashClosingService.close(closing, "Pedido", owner.getId()));
        assertWaits(close);

        releaseFirst.countDown();
        assertEquals(new BigDecimal("180.00"), close.get(10, TimeUnit.SECONDS).cashExpenses());
    }

    @Test
    void aReceiptAMovementAndACashClosingSentTogetherAllFinish() throws Exception {
        Long closing = cashClosingService.create(closing(), owner.getId()).id();
        // A movement holds the beer's lock
        holdOpen(() -> stockMovementService.create(new StockMovementRequestDTO(beer, StockMovementType.CONSUMPTION,
                BigDecimal.ONE, null, null, null), owner.getId()));

        // The receipt takes the establishment and the order, then waits for the beer
        Future<PurchaseOrderResponseDTO> receipt = pool.submit(() -> purchaseOrderService.receive(orderId,
                receipt(false, null), owner.getId()));
        assertWaits(receipt);
        // The closing waits for the establishment, which the receipt holds
        Future<CashClosingResponseDTO> close = pool.submit(() -> cashClosingService.close(closing, null, owner.getId()));
        assertWaits(close);

        releaseFirst.countDown();
        assertEquals(PurchaseOrderStatus.RECEIVED, receipt.get(10, TimeUnit.SECONDS).status());
        close.get(10, TimeUnit.SECONDS);
        // The receipt started from the stock the movement left
        assertEquals(new BigDecimal("1.000"), productService.findById(beer).currentStock());
    }

    @Test
    void deletingASupplierWhileAnOrderToItIsBeingCreatedAnswersItsConflict() throws Exception {
        Long makro = supplierService.create(new SupplierRequestDTO(venue.getId(), "Makro", null, null, null)).id();
        // An order to Makro is saved but not committed yet
        holdOpen(() -> purchaseOrderService.create(new PurchaseOrderRequestDTO(venue.getId(), makro, null, null,
                List.of(new PurchaseOrderLineRequestDTO(beer, BigDecimal.ONE, null))), owner.getId()));

        // The delete doesn't see the order, gets past its check and waits for the order's transaction
        Future<?> delete = pool.submit(() -> supplierService.delete(makro));
        assertWaits(delete);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> delete.get(10, TimeUnit.SECONDS));
        BusinessRuleException refused = assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals("Cannot delete a supplier that has purchase orders", refused.getMessage());
        assertEquals(ErrorCode.SUPPLIER_HAS_ORDERS, refused.getCode());
        assertTrue(supplierRepository.existsById(makro));
    }

    @Test
    void deletingAnEstablishmentWhileAMovementIsBeingRecordedAnswersItsConflict() throws Exception {
        Establishment other = createEstablishment(owner);
        Long rum = productService.create(new ProductRequestDTO(other.getId(), "Ron", ProductCategory.SPIRITS,
                ProductUnit.BOTTLE, null, null, null)).id();
        // A movement of its rum is recorded but not committed yet
        holdOpen(() -> stockMovementService.create(new StockMovementRequestDTO(rum, StockMovementType.PURCHASE,
                BigDecimal.ONE, null, null, null), owner.getId()));

        Future<?> delete = pool.submit(() -> establishmentService.delete(other.getId()));
        assertWaits(delete);

        releaseFirst.countDown();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> delete.get(10, TimeUnit.SECONDS));
        BusinessRuleException refused = assertInstanceOf(BusinessRuleException.class, failure.getCause());
        assertEquals("Cannot delete an establishment that has stock movements or purchase orders; keep it to keep"
                + " its inventory records", refused.getMessage());
        assertTrue(establishmentRepository.existsById(other.getId()));
        assertEquals(1, stockMovementRepository.count());
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
        assertFalse(request.isDone(), "the request should wait until the first one commits");
    }

    private PurchaseOrderReceiveRequestDTO receipt(boolean createExpense, PaymentMethod paymentMethod) {
        return new PurchaseOrderReceiveRequestDTO(DAY,
                List.of(new PurchaseOrderReceiveLineRequestDTO(lineId, new BigDecimal("2"), null)),
                createExpense, paymentMethod);
    }

    private CashClosingRequestDTO closing() {
        return new CashClosingRequestDTO(venue.getId(), DAY, new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, new BigDecimal("100"), null, null);
    }
}

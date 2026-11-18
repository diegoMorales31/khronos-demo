package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.EventRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerEventRequestDTO;
import com.moga.khronosdemo.dtos.request.WorkerShiftRequestDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
 * Concurrent schedule changes for the same worker, or for the same event, must take turns. Each test
 * keeps a first change open inside a transaction, checks that a second change is still waiting, and
 * only then lets the first one commit.
 */
class ScheduleLockingTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);
    private static final long WAIT_MILLIS = 500;

    @Autowired
    private WorkerShiftService workerShiftService;

    @Autowired
    private WorkerEventService workerEventService;

    @Autowired
    private EventService eventService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch firstDone = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);

    private Zone zone;
    private Worker worker;
    private Worker otherWorker;

    @BeforeEach
    void setUp() {
        Establishment venue = createEstablishment(createOwner("owner"));
        zone = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
        otherWorker = createWorker(venue, "other-worker");
    }

    @AfterEach
    void stopThreads() throws InterruptedException {
        releaseFirst.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void overlappingShiftsCreatedAtTheSameTimeCannotBothBeSaved() throws Exception {
        Future<?> first = holdOpen(() -> workerShiftService.create(List.of(shift(worker, "18:00", "23:00"))));

        Future<?> second = pool.submit(() -> workerShiftService.create(List.of(shift(worker, "19:00", "22:00"))));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWithBusinessRule(second);
        assertEquals(1, workerShiftRepository.count());
    }

    @Test
    void anUpdateWaitsForAConcurrentCreateForTheSameWorker() throws Exception {
        WorkerShift morning = createShift(worker, zone, DAY, "09:00", "12:00");
        Future<?> first = holdOpen(() -> workerShiftService.create(List.of(shift(worker, "18:00", "23:00"))));

        Future<?> second = pool.submit(() -> workerShiftService.update(morning.getId(), shift(worker, "20:00", "22:00")));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWithBusinessRule(second);
        assertEquals(LocalTime.of(9, 0), workerShiftRepository.findById(morning.getId()).orElseThrow().getStartTime());
    }

    @Test
    void deletingTheCoveringShiftWaitsForAnAssignmentBeingMade() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        Future<?> first = holdOpen(() -> workerEventService.create(
                List.of(new WorkerEventRequestDTO(worker.getUserId(), dinner.getId()))));

        Future<?> second = pool.submit(() -> workerShiftService.delete(evening.getId()));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        // The delete ran after the assignment was committed, saw it, and removed it with the shift
        assertFalse(workerShiftRepository.existsById(evening.getId()));
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void aSecondEditOfAShiftStartsFromWhatTheFirstOneCommitted() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        // The first change gives the shift to the other worker and assigns them to the dinner it covers
        Future<?> first = holdOpen(() -> {
            workerShiftService.update(evening.getId(), shift(otherWorker, "18:00", "23:00"));
            workerEventService.create(List.of(new WorkerEventRequestDTO(otherWorker.getUserId(), dinner.getId())));
        });

        Future<?> second = pool.submit(() -> workerShiftService.update(evening.getId(), shift(otherWorker, "20:00", "23:00")));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        // The second edit knew the shift was already the other worker's, so it checked their assignments:
        // 20:00-23:00 no longer covers the dinner at 19:00
        assertEquals(otherWorker.getUserId(), workerShiftRepository.findWorkerIdById(evening.getId()).orElseThrow());
        assertEquals(LocalTime.of(20, 0), workerShiftRepository.findById(evening.getId()).orElseThrow().getStartTime());
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void anEditOfAShiftDeletedMeanwhileFindsNoShift() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Future<?> first = holdOpen(() -> workerShiftService.delete(evening.getId()));

        Future<?> second = pool.submit(() -> workerShiftService.update(evening.getId(), shift(worker, "19:00", "23:00")));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWith(ResourceNotFoundException.class, second);
        assertEquals(0, workerShiftRepository.count());
    }

    @Test
    void aShiftDeletedTwiceAtTheSameTimeIsDeletedOnce() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Future<?> first = holdOpen(() -> workerShiftService.delete(evening.getId()));

        Future<?> second = pool.submit(() -> workerShiftService.delete(evening.getId()));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWith(ResourceNotFoundException.class, second);
    }

    @Test
    void anEditIsRefusedWhenTheShiftWasGivenToAWorkerItDidNotLock() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Future<?> first = holdOpen(() -> workerShiftService.update(evening.getId(), shift(otherWorker, "18:00", "23:00")));

        // Sent for the shift as it was: still the first worker's, so only that worker is locked
        Future<?> second = pool.submit(() -> workerShiftService.update(evening.getId(), shift(worker, "19:00", "23:00")));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWithBusinessRule(second);
        assertEquals(otherWorker.getUserId(), workerShiftRepository.findWorkerIdById(evening.getId()).orElseThrow());
        assertEquals(LocalTime.of(18, 0), workerShiftRepository.findById(evening.getId()).orElseThrow().getStartTime());
    }

    @Test
    void movingAnEventWaitsForAnAssignmentBeingMadeToIt() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        Future<?> first = holdOpen(() -> workerEventService.create(
                List.of(new WorkerEventRequestDTO(worker.getUserId(), dinner.getId()))));

        Future<?> second = pool.submit(() -> eventService.update(dinner.getId(), moved(dinner, DAY.atTime(23, 30))));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        // The move ran after the assignment was committed, saw it, and removed it: the shift ends at 23:00
        assertEquals(DAY.atTime(23, 30), eventRepository.findById(dinner.getId()).orElseThrow().getDateTime());
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void anAssignmentWaitsForAMoveOfItsEventAndIsCheckedAgainstTheNewTime() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        Future<?> first = holdOpen(() -> eventService.update(dinner.getId(), moved(dinner, DAY.atTime(23, 30))));

        Future<?> second = pool.submit(() -> workerEventService.create(
                List.of(new WorkerEventRequestDTO(worker.getUserId(), dinner.getId()))));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertFailsWithBusinessRule(second);
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void deletingAnEventWaitsForAnAssignmentBeingMadeToIt() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        Future<?> first = holdOpen(() -> workerEventService.create(
                List.of(new WorkerEventRequestDTO(worker.getUserId(), dinner.getId()))));

        Future<?> second = pool.submit(() -> eventService.delete(dinner.getId()));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        // The delete ran after the assignment was committed, saw it, and took it with the event
        second.get(10, TimeUnit.SECONDS);
        assertFalse(eventRepository.existsById(dinner.getId()));
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void aShiftChangeWaitsForAMoveOfAnEventItsWorkerIsAssignedTo() throws Exception {
        WorkerShift evening = createShift(worker, zone, DAY, "18:00", "23:00");
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        assign(worker, dinner);
        Future<?> first = holdOpen(() -> eventService.update(dinner.getId(), moved(dinner, DAY.atTime(20, 0))));

        Future<?> second = pool.submit(() -> workerShiftService.update(evening.getId(), shift(worker, "21:00", "23:00")));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        // The shift change saw the event at 20:00, which 21:00-23:00 no longer covers
        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void changesForDifferentWorkersDoNotWaitForEachOther() throws Exception {
        Future<?> first = holdOpen(() -> workerShiftService.create(List.of(shift(worker, "18:00", "23:00"))));

        Future<?> second = pool.submit(() -> workerShiftService.create(List.of(shift(otherWorker, "18:00", "23:00"))));
        second.get(10, TimeUnit.SECONDS);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertEquals(2, workerShiftRepository.count());
    }

    @Test
    void lockingASingleRowWaitsForTheSameLockAndFindsNothingForAMissingId() throws Exception {
        Event dinner = createEvent(zone, DAY.atTime(19, 0));
        // Moving the event locks it; creating the shift locks the worker
        Future<?> first = holdOpen(() -> {
            eventService.update(dinner.getId(), moved(dinner, DAY.atTime(20, 0)));
            workerShiftService.create(List.of(shift(worker, "18:00", "23:00")));
        });

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<Boolean> second = pool.submit(() -> transaction.execute(status ->
                eventRepository.lockById(dinner.getId()).isPresent()
                        && workerRepository.lockById(worker.getUserId()).isPresent()));
        assertStillWaiting(second);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertTrue(second.get(10, TimeUnit.SECONDS));
        Boolean noneMissing = transaction.execute(status ->
                eventRepository.lockById(-1L).isEmpty() && workerRepository.lockById(-1L).isEmpty());
        assertTrue(noneMissing);
    }

    /** Runs the change inside a transaction that stays open, holding its locks, until releaseFirst. */
    private Future<?> holdOpen(Runnable change) throws InterruptedException {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> future = pool.submit(() -> transaction.executeWithoutResult(status -> {
            change.run();
            firstDone.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(firstDone.await(10, TimeUnit.SECONDS), "the first change did not finish");
        return future;
    }

    private static void assertStillWaiting(Future<?> second) throws InterruptedException {
        Thread.sleep(WAIT_MILLIS);
        assertFalse(second.isDone(), "the second change should wait until the first one commits");
    }

    private static void assertFailsWithBusinessRule(Future<?> second) {
        assertFailsWith(BusinessRuleException.class, second);
    }

    private static void assertFailsWith(Class<? extends Exception> expected, Future<?> second) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
        assertInstanceOf(expected, failure.getCause());
    }

    private EventRequestDTO moved(Event event, LocalDateTime dateTime) {
        return new EventRequestDTO(event.getTitle(), dateTime, zone.getId(), event.getPeopleNumber(), event.getDescription());
    }

    private WorkerShiftRequestDTO shift(Worker worker, String start, String end) {
        return new WorkerShiftRequestDTO(worker.getUserId(), DAY, LocalTime.parse(start), LocalTime.parse(end), zone.getId());
    }
}

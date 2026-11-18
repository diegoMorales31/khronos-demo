package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner A, and A's workers, must get 403 for the time entries, rates and reports of owner B's venue,
 * and nothing of B's may change.
 */
class TimeTrackingIsolationTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);
    private static final String RANGE = "?from=2030-05-01&to=2030-05-31";

    private User ownerA;
    private Worker workerA;
    private Establishment venueB;
    private Worker workerB;
    private TimeEntry entryB;
    private WorkerRate rateB;

    @BeforeEach
    void setUp() {
        ownerA = createOwner("owner-a");
        Establishment venueA = createEstablishment(ownerA);
        workerA = createWorker(venueA, "worker-a");

        venueB = createEstablishment(createOwner("owner-b"));
        Zone zoneB = createZone(venueB, "Kitchen");
        workerB = createWorker(venueB, "worker-b");
        entryB = createTimeEntry(workerB, createShift(workerB, zoneB, DAY, "18:00", "23:00"), DAY,
                "2030-05-10T16:00:00Z", "2030-05-10T21:00:00Z", 0);
        rateB = workerRateRepository.save(new WorkerRate(workerB, new BigDecimal("11.00"), DAY, Instant.now()));
        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));
    }

    @Test
    void anOwnerCannotReadOrChangeAnotherOwnersTimeEntries() throws Exception {
        expectForbidden(as(ownerA), postJson("/api/time-entries", """
                { "workerId": %d, "clockIn": "2030-05-11T16:00:00Z", "reason": "Hijack" }
                """.formatted(workerB.getUserId())));
        expectForbidden(as(ownerA), putJson("/api/time-entries/{id}", """
                { "clockIn": "2030-05-10T10:00:00Z", "clockOut": "2030-05-10T21:00:00Z", "breakMinutes": 0, "reason": "Hijack" }
                """, entryB.getId()));
        expectForbidden(as(ownerA), putJson("/api/time-entries/{id}/void", """
                { "reason": "Hijack" }
                """, entryB.getId()));
        expectForbidden(as(ownerA), postJson("/api/time-entries/{id}/clock-out", """
                { "reason": "Hijack" }
                """, entryB.getId()));
        expectForbidden(as(ownerA), getJson("/api/time-entries/{id}/history", entryB.getId()));
        expectForbidden(as(ownerA), getJson("/api/time-entries/worker/{id}", workerB.getUserId()));
        expectForbidden(as(ownerA), getJson("/api/time-entries/establishment/{id}", venueB.getId()));
        expectForbidden(as(ownerA), getJson("/api/time-entries/establishment/{id}/open", venueB.getId()));

        assertEntryUnchanged();
    }

    @Test
    void anOwnerCannotReadOrChangeAnotherOwnersRates() throws Exception {
        expectForbidden(as(ownerA), getJson("/api/workers/{id}/rates", workerB.getUserId()));
        expectForbidden(as(ownerA), postJson("/api/workers/{id}/rates", """
                { "hourlyRate": 1, "effectiveFrom": "2030-06-01" }
                """, workerB.getUserId()));
        expectForbidden(as(ownerA), deleteJson("/api/workers/{id}/rates/{rateId}", workerB.getUserId(), rateB.getId()));

        // Going through one of A's own workers does not reach B's rate either
        mockMvc.perform(deleteJson("/api/workers/{id}/rates/{rateId}", workerA.getUserId(), rateB.getId()).with(as(ownerA)))
                .andExpect(status().isNotFound());

        assertEquals(1, workerRateRepository.count());
        assertTrue(workerRateRepository.existsById(rateB.getId()));
    }

    @Test
    void anOwnerCannotReadAnotherOwnersReports() throws Exception {
        expectForbidden(as(ownerA), getJson("/api/reports/establishment/{id}/hours" + RANGE, venueB.getId()));
        expectForbidden(as(ownerA), getJson("/api/reports/worker/{id}/days" + RANGE, workerB.getUserId()));
        expectForbidden(as(ownerA), get("/api/reports/establishment/{id}/hours.csv" + RANGE, venueB.getId()));
        expectForbidden(as(ownerA), get("/api/reports/establishment/{id}/time-entries.csv" + RANGE, venueB.getId()));
        expectForbidden(as(ownerA), get("/api/reports/worker/{id}/time-entries.csv" + RANGE, workerB.getUserId()));
    }

    @Test
    void anotherVenuesWorkerCannotReachAnyOfIt() throws Exception {
        RequestPostProcessor worker = as(workerA);
        expectForbidden(worker, getJson("/api/time-entries/{id}/history", entryB.getId()));
        expectForbidden(worker, getJson("/api/time-entries/worker/{id}", workerB.getUserId()));
        expectForbidden(worker, getJson("/api/time-entries/establishment/{id}", venueB.getId()));
        expectForbidden(worker, putJson("/api/time-entries/{id}/void", """
                { "reason": "Hijack" }
                """, entryB.getId()));
        expectForbidden(worker, postJson("/api/time-entries/{id}/clock-out", """
                { "reason": "Hijack" }
                """, entryB.getId()));
        expectForbidden(worker, getJson("/api/workers/{id}/rates", workerB.getUserId()));
        expectForbidden(worker, getJson("/api/reports/worker/{id}/days" + RANGE, workerB.getUserId()));
        expectForbidden(worker, getJson("/api/reports/establishment/{id}/hours" + RANGE, venueB.getId()));
        expectForbidden(worker, get("/api/reports/worker/{id}/time-entries.csv" + RANGE, workerB.getUserId()));

        assertEntryUnchanged();
    }

    @Test
    void aWorkerIsForbiddenFromRatesAndVenueEntriesBeforeTheirRequestIsValidated() throws Exception {
        // Each of these would answer 400 to an owner: a body or parameters that fail validation
        RequestPostProcessor worker = as(workerA);
        expectForbidden(worker, postJson("/api/workers/{id}/rates", "{}", workerA.getUserId()));
        expectForbidden(worker, getJson("/api/workers/{id}/rates", workerA.getUserId()));
        expectForbidden(worker, deleteJson("/api/workers/{id}/rates/{rateId}", workerA.getUserId(), rateB.getId()));
        expectForbidden(worker, getJson("/api/time-entries/establishment/{id}?from=nope",
                workerA.getEstablishment().getId()));
        expectForbidden(worker, getJson("/api/time-entries/establishment/{id}/open", workerA.getEstablishment().getId()));

        // Their own entries are still theirs to read, and validated as before
        mockMvc.perform(getJson("/api/time-entries/worker/{id}", workerA.getUserId()).with(worker))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/time-entries/worker/{id}?from=nope", workerA.getUserId()).with(worker))
                .andExpect(status().isBadRequest());
        assertEntryUnchanged();
    }

    private void expectForbidden(RequestPostProcessor caller, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    private void assertEntryUnchanged() {
        TimeEntry unchanged = timeEntryRepository.findById(entryB.getId()).orElseThrow();
        assertEquals(Instant.parse("2030-05-10T16:00:00Z"), unchanged.getClockIn());
        assertNull(unchanged.getVoidedAt());
        assertEquals(1, timeEntryRepository.count());
        assertEquals(0, timeEntryChangeRepository.count());
    }
}

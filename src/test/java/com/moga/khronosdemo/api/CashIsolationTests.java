package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cash closings, expenses, the summary and their exports are only for the establishment's owner and
 * admins. A worker gets 403 everywhere, even for their own venue, and owner A gets 403 for everything
 * of owner B's venue, also when B's venue is named in a request body. Nothing of B's may change.
 */
class CashIsolationTests extends IntegrationTest {

    private static final String RANGE = "?from=2030-05-01&to=2030-05-31";

    private User ownerA;
    private Establishment venueA;
    private Worker workerA;
    private User ownerB;
    private Establishment venueB;
    private Worker workerB;
    private Long closedB;
    private Long draftB;
    private Long expenseB;

    @BeforeEach
    void setUp() throws Exception {
        ownerA = createOwner("owner-a");
        venueA = createEstablishment(ownerA);
        workerA = createWorker(venueA, "worker-a");
        ownerB = createOwner("owner-b");
        venueB = createEstablishment(ownerB);
        workerB = createWorker(venueB, "worker-b");
        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));

        expenseB = longAt(mockMvc.perform(postJson("/api/expenses", expense(venueB, "CASH")).with(as(ownerB)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        closedB = longAt(mockMvc.perform(postJson("/api/cash-closings", closing(venueB, "2030-05-10")).with(as(ownerB)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(put("/api/cash-closings/{id}/close", closedB).with(as(ownerB))).andExpect(status().isOk());
        draftB = longAt(mockMvc.perform(postJson("/api/cash-closings", closing(venueB, "2030-05-11")).with(as(ownerB)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    @Test
    void aWorkerCannotReachAnyOfItEvenForTheirOwnVenue() throws Exception {
        // Each caller gets fresh requests: a request builder keeps what was added to it
        for (MockHttpServletRequestBuilder request : everyEndpoint(venueB, closedB, draftB, expenseB)) {
            expectForbidden(as(workerB), request);
        }
        for (MockHttpServletRequestBuilder request : everyEndpoint(venueB, closedB, draftB, expenseB)) {
            expectForbidden(as(workerA), request);
        }
        assertNothingOfBChanged();
    }

    @Test
    void aWorkerIsForbiddenBeforeTheirRequestIsValidated() throws Exception {
        // Each of these would answer 400 to an owner of venue A: a body or parameters that fail validation
        RequestPostProcessor worker = as(workerA);
        expectForbidden(worker, postJson("/api/cash-closings", "{}"));
        expectForbidden(worker, putJson("/api/cash-closings/{id}", "{}", draftB));
        expectForbidden(worker, getJson("/api/cash-closings/establishment/{id}?from=2030-05-10&to=2030-05-01", venueA.getId()));
        expectForbidden(worker, postJson("/api/expenses", "{}"));
        expectForbidden(worker, putJson("/api/expenses/{id}", "{ \"amount\": -1 }", expenseB));
        expectForbidden(worker, getJson("/api/expenses/establishment/{id}?category=FOOD", venueA.getId()));
        expectForbidden(worker, getJson("/api/reports/establishment/{id}/summary", venueA.getId()));
        expectForbidden(worker, getJson("/api/reports/establishment/{id}/summary?from=2030-05-01", venueA.getId()));
        expectForbidden(worker, get("/api/reports/establishment/{id}/cash-closings.csv", venueA.getId()));
        expectForbidden(worker, getJson("/api/reports/establishment/{id}/hours?from=nope&to=nope", venueA.getId()));

        // An owner still gets the 400
        mockMvc.perform(postJson("/api/cash-closings", "{}").with(as(ownerA)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary", venueA.getId()).with(as(ownerA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'from'"));

        // The worker's own daily report is still theirs to read, and validated as before
        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, workerA.getUserId()).with(worker))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/reports/worker/{id}/days", workerA.getUserId()).with(worker))
                .andExpect(status().isBadRequest());

        // Without a token it is still 401
        mockMvc.perform(postJson("/api/cash-closings", "{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertNothingOfBChanged();
    }

    @Test
    void anOwnerCannotReachAnotherOwnersCash() throws Exception {
        for (MockHttpServletRequestBuilder request : everyEndpoint(venueB, closedB, draftB, expenseB)) {
            expectForbidden(as(ownerA), request);
        }
        assertNothingOfBChanged();
    }

    @Test
    void anOwnerCannotNameAnotherOwnersVenueInABody() throws Exception {
        expectForbidden(as(ownerA), postJson("/api/cash-closings", closing(venueB, "2030-05-09")));
        expectForbidden(as(ownerA), postJson("/api/expenses", expense(venueB, "CARD")));

        // An update ignores the establishment in the body, so A's records stay in A's venue
        Long closingA = longAt(mockMvc.perform(postJson("/api/cash-closings", closing(venueA, "2030-05-10")).with(as(ownerA)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(putJson("/api/cash-closings/{id}", closing(venueB, "2030-05-11"), closingA).with(as(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venueA.getId()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-10"));
        Long expenseA = longAt(mockMvc.perform(postJson("/api/expenses", expense(venueA, "CARD")).with(as(ownerA)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(putJson("/api/expenses/{id}", expense(venueB, "CARD"), expenseA).with(as(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venueA.getId()));

        // B's lists don't show A's records either
        mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venueB.getId()).with(as(ownerB)))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venueB.getId()).with(as(ownerB)))
                .andExpect(jsonPath("$.length()").value(1));
        assertNothingOfBChanged();
    }

    @Test
    void aMissingRecordIsForbiddenToAnOwnerAndNotFoundForAnAdmin() throws Exception {
        long missing = closedB + expenseB + 1000;
        expectForbidden(as(ownerA), getJson("/api/cash-closings/{id}", missing));
        expectForbidden(as(ownerA), deleteJson("/api/expenses/{id}", missing));
        User admin = createAdmin("admin");
        mockMvc.perform(getJson("/api/cash-closings/{id}", missing).with(as(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(deleteJson("/api/expenses/{id}", missing).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdminReachesEveryVenue() throws Exception {
        User admin = createAdmin("admin");
        mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/cash-closings/{id}/history", closedB).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/establishment/{id}/expenses.csv" + RANGE, venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/api/expenses", expense(venueB, "CARD")).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("First admin Last admin"));
    }

    // Every endpoint of this release, aimed at venue B and its records
    private List<MockHttpServletRequestBuilder> everyEndpoint(Establishment venue, Long closed, Long draft, Long expense) {
        return List.of(
                getJson("/api/cash-closings/establishment/{id}", venue.getId()),
                getJson("/api/cash-closings/{id}", closed),
                postJson("/api/cash-closings", closing(venue, "2030-05-09")),
                putJson("/api/cash-closings/{id}", closing(venue, "2030-05-11"), draft),
                putJson("/api/cash-closings/{id}/close", "{}", draft),
                putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": "Hijack" }
                        """, closed),
                deleteJson("/api/cash-closings/{id}", draft),
                getJson("/api/cash-closings/{id}/history", closed),
                getJson("/api/expenses/establishment/{id}", venue.getId()),
                postJson("/api/expenses", expense(venue, "CARD")),
                putJson("/api/expenses/{id}", expense(venue, "CARD"), expense),
                deleteJson("/api/expenses/{id}", expense),
                getJson("/api/reports/establishment/{id}/summary" + RANGE, venue.getId()),
                get("/api/reports/establishment/{id}/cash-closings.csv" + RANGE, venue.getId()),
                get("/api/reports/establishment/{id}/expenses.csv" + RANGE, venue.getId()),
                get("/api/reports/establishment/{id}/summary.csv" + RANGE, venue.getId()));
    }

    private void expectForbidden(RequestPostProcessor caller, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    private void assertNothingOfBChanged() {
        CashClosing closed = cashClosingRepository.findById(closedB).orElseThrow();
        assertTrue(closed.isClosed());
        CashClosing draft = cashClosingRepository.findById(draftB).orElseThrow();
        assertEquals(new BigDecimal("150.00"), draft.getCountedCash());
        assertEquals(2, cashClosingRepository.findByEstablishment(venueB.getId(), null, null).size());
        assertEquals(2, cashClosingChangeRepository.findByCashClosing_IdOrderByChangedAtAscIdAsc(closedB).size());
        assertEquals(1, cashClosingChangeRepository.findByCashClosing_IdOrderByChangedAtAscIdAsc(draftB).size());
        Expense expense = expenseRepository.findById(expenseB).orElseThrow();
        assertEquals(new BigDecimal("12.00"), expense.getAmount());
        assertEquals(1, expenseRepository.findByEstablishment(venueB.getId(), null, null, null).size());
    }

    private static String closing(Establishment venue, String date) {
        return """
                { "establishmentId": %d, "businessDate": "%s", "openingFloat": 100, "cashSales": 50,
                  "cardSales": 20, "countedCash": 150 }
                """.formatted(venue.getId(), date);
    }

    private static String expense(Establishment venue, String paymentMethod) {
        return """
                { "establishmentId": %d, "date": "2030-05-09", "category": "SUPPLIES", "description": "Ice",
                  "amount": 12, "paymentMethod": "%s" }
                """.formatted(venue.getId(), paymentMethod);
    }
}

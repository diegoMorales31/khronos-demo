package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The daily cash closing: figures worked out from the day's cash expenses, one closing per day, the
 * difference threshold, frozen figures, the history and what can be deleted. The venue is in Madrid and
 * "now" is Friday 10 May 2030 at 20:00 UTC, 22:00 in Madrid.
 * <p>
 * Every closing below has a float of 150.00, cash sales of 820.50, card sales of 1300.25 and 200.00 taken
 * out of the till, so it expects 770.50 in cash minus the day's cash expenses, and its total sales are
 * 2120.75.
 */
class CashClosingTests extends IntegrationTest {

    private static final String WED = "2030-05-08";
    private static final String THU = "2030-05-09";
    private static final String FRI = "2030-05-10";
    private static final String SAT = "2030-05-11";

    private User owner;
    private Establishment venue;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T20:00:00Z"));
    }

    @Test
    void createsADraftWhoseFiguresFollowTheDaysCashExpenses() throws Exception {
        expense(FRI, "CASH", "12.30");
        expense(FRI, "CASH", "7.70");
        expense(FRI, "CARD", "50");
        expense(THU, "CASH", "5");

        MvcResult created = mockMvc.perform(postJson("/api/cash-closings", closing(venue, FRI, "748")).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.businessDate").value(FRI))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.openingFloat").value(150.0))
                .andExpect(jsonPath("$.cashSales").value(820.5))
                .andExpect(jsonPath("$.cardSales").value(1300.25))
                .andExpect(jsonPath("$.otherSales").value(0.0))
                .andExpect(jsonPath("$.cashWithdrawals").value(200.0))
                .andExpect(jsonPath("$.countedCash").value(748.0))
                .andExpect(jsonPath("$.tips").value(35.5))
                .andExpect(jsonPath("$.notes").value("Busy night"))
                // Only the cash expenses of that day count
                .andExpect(jsonPath("$.cashExpenses").value(20.0))
                .andExpect(jsonPath("$.totalSales").value(2120.75))
                .andExpect(jsonPath("$.expectedCash").value(750.5))
                .andExpect(jsonPath("$.difference").value(-2.5))
                .andExpect(jsonPath("$.differenceReason").value(nullValue()))
                .andExpect(jsonPath("$.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.closedByName").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2030-05-10T20:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2030-05-10T20:00:00Z"))
                .andReturn();
        String json = created.getResponse().getContentAsString();
        assertTrue(json.contains("\"otherSales\":0.00"), json);
        assertTrue(json.contains("\"cashExpenses\":20.00"), json);
        assertTrue(json.contains("\"difference\":-2.50"), json);
        Long id = longAt(created, "$.id");

        // A draft is worked out again on every read
        expense(FRI, "CASH", "10");
        mockMvc.perform(getJson("/api/cash-closings/{id}", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashExpenses").value(30.0))
                .andExpect(jsonPath("$.expectedCash").value(740.5))
                .andExpect(jsonPath("$.difference").value(7.5));
        mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].cashExpenses").value(30.0))
                .andExpect(jsonPath("$[0].difference").value(7.5));
    }

    @Test
    void validatesTheRequests() throws Exception {
        mockMvc.perform(postJson("/api/cash-closings", "{}").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.businessDate").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.openingFloat").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.cashSales").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.cardSales").value("must not be null"))
                // A draft can be saved before the cash is counted
                .andExpect(jsonPath("$.fieldErrors.countedCash").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors.otherSales").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors.cashWithdrawals").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors.tips").doesNotExist());

        mockMvc.perform(postJson("/api/cash-closings", closing(venue, FRI, "748")
                        .replace("\"cashSales\": 820.50", "\"cashSales\": -1")
                        .replace("\"cardSales\": 1300.25", "\"cardSales\": 1.234")
                        .replace("\"tips\": 35.50", "\"tips\": -2")
                        .replace("\"Busy night\"", "\"" + "n".repeat(501) + "\"")).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cashSales").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors.cardSales").value(containsString("numeric value out of bounds")))
                .andExpect(jsonPath("$.fieldErrors.tips").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors.notes").value("size must be between 0 and 500"));
        assertEquals(0, cashClosingRepository.count());

        // An update needs neither the establishment nor the day
        Long id = create(venue, FRI, "748");
        mockMvc.perform(putJson("/api/cash-closings/{id}", """
                        { "openingFloat": 100, "cashSales": 0, "cardSales": 0, "countedCash": 100 }
                        """, id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherSales").value(0.0))
                .andExpect(jsonPath("$.tips").value(nullValue()))
                .andExpect(jsonPath("$.difference").value(0.0));

        mockMvc.perform(putJson("/api/cash-closings/{id}/close", """
                        { "differenceReason": "%s" }
                        """.formatted("r".repeat(256)), id).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.differenceReason").value("size must be between 0 and 255"));
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", "{}", id).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").value("must not be blank"));
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": "ab" }
                        """, id).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").value("size must be between 3 and 255"));
    }

    @Test
    void aDraftCanBeSavedBeforeTheCashIsCountedButNotClosed() throws Exception {
        expense(FRI, "CASH", "20");
        String uncounted = closing(venue, FRI, "null");

        MvcResult created = mockMvc.perform(postJson("/api/cash-closings", uncounted).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.countedCash").value(nullValue()))
                .andExpect(jsonPath("$.cashExpenses").value(20.0))
                .andExpect(jsonPath("$.expectedCash").value(750.5))
                .andExpect(jsonPath("$.difference").value(nullValue()))
                .andReturn();
        String json = created.getResponse().getContentAsString();
        assertTrue(json.contains("\"countedCash\":null"), json);
        assertTrue(json.contains("\"difference\":null"), json);
        Long id = longAt(created, "$.id");
        mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].countedCash").value(nullValue()))
                .andExpect(jsonPath("$[0].difference").value(nullValue()));

        // It can't be closed until the cash is counted
        mockMvc.perform(put("/api/cash-closings/{id}/close", id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cash has not been counted; enter countedCash before"
                        + " closing the day"))
                .andExpect(jsonPath("$.code").value("CASH_NOT_COUNTED"));
        assertFalse(cashClosingRepository.findById(id).orElseThrow().isClosed());

        clock.advance(Duration.ofMinutes(1));
        update(id, "748", owner);
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(put("/api/cash-closings/{id}/close", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedCash").value(748.0))
                .andExpect(jsonPath("$.difference").value(-2.5));

        mockMvc.perform(getJson("/api/cash-closings/{id}/history", id).with(as(owner)))
                .andExpect(jsonPath("$[*].action", contains("CREATED", "UPDATED", "CLOSED")))
                .andExpect(jsonPath("$[*].countedCash", contains(null, 748.0, 748.0)))
                .andExpect(jsonPath("$[*].difference", contains(null, -2.5, -2.5)));

        // An edit of a draft that leaves countedCash out clears it, as PUT replaces every amount
        Long thursday = create(venue, THU, "770.50");
        mockMvc.perform(putJson("/api/cash-closings/{id}", """
                        { "openingFloat": 150, "cashSales": 820.50, "cardSales": 1300.25, "cashWithdrawals": 200 }
                        """, thursday).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedCash").value(nullValue()))
                .andExpect(jsonPath("$.difference").value(nullValue()));

        // The database won't take a closed day without counted cash either
        DataIntegrityViolationException closedUncounted = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("update cash_closings set counted_cash = null where id = ?", id));
        assertTrue(closedUncounted.getMostSpecificCause().getMessage().contains("ck_cash_closings_counted_when_closed"));
    }

    @Test
    void anEstablishmentHasOneClosingPerDay() throws Exception {
        create(venue, FRI, "748");

        mockMvc.perform(postJson("/api/cash-closings", closing(venue, FRI, "770.50")).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A cash closing already exists for 2030-05-10 at this"
                        + " establishment; edit that one instead"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_EXISTS"))
                .andExpect(jsonPath("$.details.date").value(FRI));

        create(venue, THU, "748");
        create(createEstablishment(owner), FRI, "748");
        assertEquals(3, cashClosingRepository.count());
    }

    @Test
    void theDayCannotBeInTheFutureOfTheVenuesTimeZone() throws Exception {
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        mockMvc.perform(postJson("/api/cash-closings", closing(venue, SAT, "770.50")).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The business date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"))
                .andExpect(jsonPath("$.details").doesNotExist());

        // At 12:00 UTC it is already 02:00 on Saturday in Kiritimati (UTC+14)
        Establishment kiritimati = createEstablishment(owner);
        kiritimati.setTimeZone("Pacific/Kiritimati");
        establishmentRepository.save(kiritimati);
        create(kiritimati, SAT, "770.50");

        // And at 23:30 UTC it is Saturday in Madrid
        clock.setNow(Instant.parse("2030-05-10T23:30:00Z"));
        create(venue, SAT, "770.50");
    }

    @Test
    void editsADraftButNeverItsDayOrEstablishment() throws Exception {
        Long id = create(venue, FRI, "748");
        clock.advance(Duration.ofMinutes(5));

        mockMvc.perform(putJson("/api/cash-closings/{id}", """
                        { "establishmentId": %d, "businessDate": "%s", "openingFloat": 100, "cashSales": 500,
                          "cardSales": 700, "otherSales": 30.10, "cashWithdrawals": 0, "countedCash": 590,
                          "tips": null, "notes": "  " }
                        """.formatted(createEstablishment(owner).getId(), THU), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.businessDate").value(FRI))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.otherSales").value(30.1))
                .andExpect(jsonPath("$.totalSales").value(1230.1))
                .andExpect(jsonPath("$.expectedCash").value(600.0))
                .andExpect(jsonPath("$.difference").value(-10.0))
                .andExpect(jsonPath("$.tips").value(nullValue()))
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2030-05-10T20:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2030-05-10T20:05:00Z"));
    }

    @Test
    void closesADifferenceWithinTheThresholdWithoutAReason() throws Exception {
        Long id = create(venue, FRI, "775.50");
        clock.advance(Duration.ofMinutes(10));

        // +5.00 is not over the 5.00 threshold, and the body is optional
        mockMvc.perform(put("/api/cash-closings/{id}/close", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.difference").value(5.0))
                .andExpect(jsonPath("$.differenceReason").value(nullValue()))
                .andExpect(jsonPath("$.closedAt").value("2030-05-10T20:10:00Z"))
                .andExpect(jsonPath("$.closedByName").value("First owner Last owner"))
                .andExpect(jsonPath("$.updatedAt").value("2030-05-10T20:10:00Z"));
    }

    @Test
    void aDifferenceOverTheThresholdNeedsAReason() throws Exception {
        Long id = create(venue, FRI, "765.49");
        String message = "The cash difference of -5.01 EUR is larger than the 5.00 EUR allowed without a reason;"
                + " send a differenceReason to close the day";

        mockMvc.perform(putJson("/api/cash-closings/{id}/close", "{}", id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.code").value("DIFFERENCE_REASON_REQUIRED"));
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", """
                        { "differenceReason": "   " }
                        """, id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.code").value("DIFFERENCE_REASON_REQUIRED"));
        assertFalse(cashClosingRepository.findById(id).orElseThrow().isClosed());

        mockMvc.perform(putJson("/api/cash-closings/{id}/close", """
                        { "differenceReason": " A 5 euro note given as change twice " }
                        """, id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.difference").value(-5.01))
                .andExpect(jsonPath("$.differenceReason").value("A 5 euro note given as change twice"));
    }

    @Test
    void aClosedDayKeepsItsFiguresAndLocksItsCashExpenses() throws Exception {
        Long cashFriday = expense(FRI, "CASH", "10");
        Long cardFriday = expense(FRI, "CARD", "50");
        Long cashThursday = expense(THU, "CASH", "5");
        Long id = create(venue, FRI, "760.50");
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", "{}", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashExpenses").value(10.0))
                .andExpect(jsonPath("$.expectedCash").value(760.5))
                .andExpect(jsonPath("$.difference").value(0.0));

        String locked = "The cash closing of 2030-05-10 is closed; reopen it to add, change or delete cash expenses"
                + " of that day";
        // Adding, changing, deleting or moving in a cash expense of that day; the closed day comes in details
        mockMvc.perform(postJson("/api/expenses", expenseJson(FRI, "CASH", "1")).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(locked))
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"))
                .andExpect(jsonPath("$.details.date").value(FRI));
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(FRI, "CASH", "11"), cashFriday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(locked))
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"));
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(FRI, "CARD", "10"), cashFriday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"));
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(THU, "CASH", "10"), cashFriday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"))
                .andExpect(jsonPath("$.details.date").value(FRI));
        mockMvc.perform(deleteJson("/api/expenses/{id}", cashFriday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(locked))
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"));
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(FRI, "CASH", "50"), cardFriday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"));
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(FRI, "CASH", "5"), cashThursday).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"))
                .andExpect(jsonPath("$.details.date").value(FRI));

        // Other payment methods, and other days, are not locked
        expense(FRI, "TRANSFER", "100");
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(FRI, "CARD", "55"), cardFriday).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(deleteJson("/api/expenses/{id}", cardFriday).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(putJson("/api/expenses/{id}", expenseJson(THU, "CASH", "6"), cashThursday).with(as(owner)))
                .andExpect(status().isOk());

        // The closing itself can't change either
        mockMvc.perform(getJson("/api/cash-closings/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.cashExpenses").value(10.0))
                .andExpect(jsonPath("$.difference").value(0.0));
        mockMvc.perform(putJson("/api/cash-closings/{id}", closing(venue, FRI, "1"), id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A closed cash closing cannot be edited; reopen it first"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_CLOSED"))
                .andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", "{}", id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cash closing is already closed"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_CLOSED"));

        // Reopened, it is a draft again and follows its expenses
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": "Forgot the ice delivery" }
                        """, id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.closedByName").value(nullValue()))
                .andExpect(jsonPath("$.differenceReason").value(nullValue()));
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": "Again" }
                        """, id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cash closing is not closed"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_NOT_CLOSED"));
        expense(FRI, "CASH", "2");
        mockMvc.perform(getJson("/api/cash-closings/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.cashExpenses").value(12.0))
                .andExpect(jsonPath("$.difference").value(2.0));
    }

    @Test
    void keepsEveryChangeInAnAppendOnlyHistory() throws Exception {
        User admin = createAdmin("admin");
        Long id = create(venue, FRI, "770.50");
        clock.advance(Duration.ofMinutes(1));
        update(id, "760", owner);
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", """
                        { "differenceReason": "Till short, counted twice" }
                        """, id).with(as(owner)))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": " Recounted the till " }
                        """, id).with(as(admin)))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        update(id, "770", owner);
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(put("/api/cash-closings/{id}/close", id).with(as(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(getJson("/api/cash-closings/{id}/history", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].action", contains("CREATED", "UPDATED", "CLOSED", "REOPENED", "UPDATED", "CLOSED")))
                .andExpect(jsonPath("$[*].changedAt", contains("2030-05-10T20:00:00Z", "2030-05-10T20:01:00Z",
                        "2030-05-10T20:02:00Z", "2030-05-10T20:03:00Z", "2030-05-10T20:04:00Z", "2030-05-10T20:05:00Z")))
                .andExpect(jsonPath("$[*].reason", contains(null, null, "Till short, counted twice", "Recounted the till",
                        null, null)))
                .andExpect(jsonPath("$[*].changedByName", contains("First owner Last owner", "First owner Last owner",
                        "First owner Last owner", "First admin Last admin", "First owner Last owner", "First owner Last owner")))
                .andExpect(jsonPath("$[3].changedByUserId").value(admin.getId()))
                .andExpect(jsonPath("$[0].changedByUserId").value(owner.getId()))
                .andExpect(jsonPath("$[*].totalSales", contains(2120.75, 2120.75, 2120.75, 2120.75, 2120.75, 2120.75)))
                .andExpect(jsonPath("$[*].countedCash", contains(770.5, 760.0, 760.0, 760.0, 770.0, 770.0)))
                .andExpect(jsonPath("$[*].difference", contains(0.0, -10.5, -10.5, -10.5, -0.5, -0.5)));

        // The database refuses to change a history row, or to delete a closing that has history
        DataAccessException update = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("update cash_closing_changes set reason = 'Rewritten' where cash_closing_id = ?", id));
        assertTrue(update.getMostSpecificCause().getMessage().contains("cash_closing_changes rows cannot be modified"));
        DataIntegrityViolationException delete = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from cash_closings where id = ?", id));
        assertTrue(delete.getMostSpecificCause().getMessage().contains("fk_cash_closing_changes_cash_closing"));
        assertEquals(6, cashClosingChangeRepository.count());

        mockMvc.perform(getJson("/api/cash-closings/{id}/history", id + 1000).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyADraftThatWasNeverClosedCanBeDeleted() throws Exception {
        User admin = createAdmin("admin");
        Long draft = create(venue, FRI, "770.50");
        update(draft, "770", owner);

        mockMvc.perform(deleteJson("/api/cash-closings/{id}", draft).with(as(owner)))
                .andExpect(status().isNoContent());
        assertFalse(cashClosingRepository.existsById(draft));
        assertEquals(0, cashClosingChangeRepository.count());

        Long closed = create(venue, THU, "770.50");
        mockMvc.perform(put("/api/cash-closings/{id}/close", closed).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(deleteJson("/api/cash-closings/{id}", closed).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A closed cash closing cannot be deleted; reopen it to correct"
                        + " it instead"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_CLOSED"));
        mockMvc.perform(putJson("/api/cash-closings/{id}/reopen", """
                        { "reason": "Wrong card total" }
                        """, closed).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(deleteJson("/api/cash-closings/{id}", closed).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A cash closing that has been closed before cannot be deleted;"
                        + " correct it and close it again instead"))
                .andExpect(jsonPath("$.code").value("CASH_CLOSING_WAS_CLOSED"));
        assertTrue(cashClosingRepository.existsById(closed));
        assertEquals(3, cashClosingChangeRepository.count());

        mockMvc.perform(deleteJson("/api/cash-closings/{id}", draft).with(as(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("CashClosing not found with id " + draft));
    }

    @Test
    void listsLatestDayFirstWithOptionalDates() throws Exception {
        Long wednesday = create(venue, WED, "770.50");
        Long friday = create(venue, FRI, "770.50");
        Long thursday = create(venue, THU, "770.50");
        create(createEstablishment(createOwner("other-owner")), THU, "770.50");

        String list = "/api/cash-closings/establishment/" + venue.getId();
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), thursday.intValue(), wednesday.intValue())));
        mockMvc.perform(getJson(list + "?from=" + THU).with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), thursday.intValue())));
        mockMvc.perform(getJson(list + "?from=" + WED + "&to=" + THU).with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(thursday.intValue(), wednesday.intValue())));
        mockMvc.perform(getJson(list + "?from=" + FRI + "&to=" + THU).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
    }

    @Test
    void aListingCoversAtMost366Days() throws Exception {
        Long friday = create(venue, FRI, "770.50");
        Long firstDay = create(venue, "2029-05-10", "770.50");
        Long dayBefore = create(venue, "2029-05-09", "770.50");
        String list = "/api/cash-closings/establishment/" + venue.getId();

        // Without dates: the 366 days up to today, Friday 10 May 2030 in Madrid
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), firstDay.intValue())));
        // With only one date, the other is 365 days away from it
        mockMvc.perform(getJson(list + "?from=2029-05-09").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        mockMvc.perform(getJson(list + "?to=2029-05-31").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        // Both: at most 366 days, as in the reports
        mockMvc.perform(getJson(list + "?from=2029-05-10&to=2030-05-10").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), firstDay.intValue())));
        mockMvc.perform(getJson(list + "?from=2029-05-09&to=2030-05-10").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));

        // Today is the venue's: at 23:30 UTC it is already Saturday in Madrid, so 10 May 2029 drops out
        clock.setNow(Instant.parse("2030-05-10T23:30:00Z"));
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(friday.intValue())));

        // An admin listing an establishment that doesn't exist, without dates
        mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venue.getId() + 1000)
                        .with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound());
    }

    private Long create(Establishment establishment, String date, String countedCash) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/cash-closings", closing(establishment, date, countedCash))
                        .with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void update(Long id, String countedCash, User user) throws Exception {
        mockMvc.perform(putJson("/api/cash-closings/{id}", closing(venue, FRI, countedCash), id).with(as(user)))
                .andExpect(status().isOk());
    }

    private Long expense(String date, String paymentMethod, String amount) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/expenses", expenseJson(date, paymentMethod, amount)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private String expenseJson(String date, String paymentMethod, String amount) {
        return """
                { "establishmentId": %d, "date": "%s", "category": "SUPPLIES", "description": "Ice",
                  "amount": %s, "paymentMethod": "%s" }
                """.formatted(venue.getId(), date, amount, paymentMethod);
    }

    private static String closing(Establishment establishment, String date, String countedCash) {
        return """
                { "establishmentId": %d, "businessDate": "%s", "openingFloat": 150, "cashSales": 820.50,
                  "cardSales": 1300.25, "cashWithdrawals": 200, "countedCash": %s, "tips": 35.50,
                  "notes": "Busy night" }
                """.formatted(establishment.getId(), date, countedCash);
    }
}

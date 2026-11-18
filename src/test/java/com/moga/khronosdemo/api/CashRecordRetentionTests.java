package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cash closings (drafts too) and expenses are an establishment's financial record, so an establishment
 * or an owner that has them can't be deleted, through the API or straight in the database. Deleting the
 * account that entered or closed something keeps that person's name on it.
 */
class CashRecordRetentionTests extends IntegrationTest {

    private User admin;
    private User owner;
    private Establishment venueWithClosing;
    private Establishment venueWithExpense;
    private Long closingId;
    private Long expenseId;

    @BeforeEach
    void setUp() throws Exception {
        admin = createAdmin("admin");
        owner = createOwner("owner");
        venueWithClosing = createEstablishment(owner);
        venueWithExpense = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T20:00:00Z"));

        closingId = longAt(mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "2030-05-10", "openingFloat": 100, "cashSales": 50,
                          "cardSales": 20, "countedCash": 150 }
                        """.formatted(venueWithClosing.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        expenseId = longAt(mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "2030-05-10", "category": "RENT", "description": "May rent",
                          "amount": 900, "paymentMethod": "TRANSFER" }
                        """.formatted(venueWithExpense.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    @Test
    void anEstablishmentWithCashClosingsOrExpensesCannotBeDeleted() throws Exception {
        String message = "Cannot delete an establishment that has cash closings or expenses; keep it to keep its"
                + " financial records";
        // Even a draft counts
        mockMvc.perform(deleteJson("/api/establishments/{id}", venueWithClosing.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));
        mockMvc.perform(deleteJson("/api/establishments/{id}", venueWithExpense.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message))
                // A conflict without a code of its own carries neither code nor details
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        assertTrue(establishmentRepository.existsById(venueWithClosing.getId()));
        assertTrue(establishmentRepository.existsById(venueWithExpense.getId()));
        assertEverythingKept();
    }

    @Test
    void anOwnerWhoseEstablishmentsHaveCashRecordsCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete an owner whose establishments have cash closings"
                        + " or expenses; keep the owner to keep those financial records"));

        assertTrue(userRepository.existsById(owner.getId()));
        assertEverythingKept();
    }

    @Test
    void withoutThemTheDeletesWorkAsBefore() throws Exception {
        mockMvc.perform(deleteJson("/api/cash-closings/{id}", closingId).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/establishments/{id}", venueWithClosing.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/expenses/{id}", expenseId).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertFalse(establishmentRepository.existsById(venueWithExpense.getId()));
    }

    @Test
    void theDatabaseRefusesToDeleteThemOrTheirEstablishment() {
        assertEquals("r", onDelete("fk_cash_closings_establishment"));
        assertEquals("r", onDelete("fk_cash_closing_changes_cash_closing"));
        assertEquals("r", onDelete("fk_expenses_establishment"));
        assertEquals("n", onDelete("fk_cash_closings_closed_by"));
        assertEquals("n", onDelete("fk_cash_closing_changes_changed_by"));
        assertEquals("n", onDelete("fk_expenses_created_by"));

        DataIntegrityViolationException closingVenue = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from establishments where id = ?", venueWithClosing.getId()));
        assertTrue(closingVenue.getMostSpecificCause().getMessage().contains("fk_cash_closings_establishment"));
        DataIntegrityViolationException expenseVenue = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from establishments where id = ?", venueWithExpense.getId()));
        assertTrue(expenseVenue.getMostSpecificCause().getMessage().contains("fk_expenses_establishment"));

        assertEverythingKept();
    }

    @Test
    void deletingTheAuthorKeepsTheirName() throws Exception {
        mockMvc.perform(put("/api/cash-closings/{id}/close", closingId).with(as(owner)))
                .andExpect(status().isOk());
        // Both venues move to a new owner, then the old owner's account is deleted
        User newOwner = createOwner("new-owner");
        for (Establishment venue : new Establishment[]{venueWithClosing, venueWithExpense}) {
            mockMvc.perform(putJson("/api/establishments/{id}", """
                            { "establishmentName": "Venue", "establishmentAddress": "%s", "ownerId": %d }
                            """.formatted("New address " + venue.getId(), newOwner.getId()), venue.getId()).with(as(admin)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(getJson("/api/cash-closings/{id}", closingId).with(as(newOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedByName").value("First owner Last owner"));
        mockMvc.perform(getJson("/api/cash-closings/{id}/history", closingId).with(as(newOwner)))
                .andExpect(jsonPath("$[*].action", contains("CREATED", "CLOSED")))
                .andExpect(jsonPath("$[*].changedByName", contains("First owner Last owner", "First owner Last owner")))
                .andExpect(jsonPath("$[1].changedByUserId").value(nullValue()));
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venueWithExpense.getId()).with(as(newOwner)))
                .andExpect(jsonPath("$[0].createdByName").value("First owner Last owner"));
    }

    private void assertEverythingKept() {
        assertTrue(cashClosingRepository.existsById(closingId));
        assertEquals(1, cashClosingChangeRepository.count());
        assertTrue(expenseRepository.existsById(expenseId));
    }

    private String onDelete(String constraint) {
        return jdbcTemplate.queryForObject("select confdeltype::text from pg_constraint where conname = ?",
                String.class, constraint);
    }
}

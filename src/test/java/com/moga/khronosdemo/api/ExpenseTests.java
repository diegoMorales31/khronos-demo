package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Expenses entered by hand: validation, the business day in the venue's time zone, filters and order.
 * "Now" is Friday 10 May 2030 at 12:00 UTC, 14:00 in Madrid.
 */
class ExpenseTests extends IntegrationTest {

    private static final LocalDate FRI = LocalDate.of(2030, 5, 10);

    private User owner;
    private Establishment venue;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
    }

    @Test
    void createsAnExpenseWithTwoDecimalsAndItsAuthor() throws Exception {
        MvcResult result = mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "2030-05-10", "category": "SUPPLIES",
                          "description": "  Beer kegs ", "supplier": " Mahou ", "amount": 120.5,
                          "paymentMethod": "CASH", "notes": "  " }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.date").value("2030-05-10"))
                .andExpect(jsonPath("$.category").value("SUPPLIES"))
                .andExpect(jsonPath("$.description").value("Beer kegs"))
                .andExpect(jsonPath("$.supplier").value("Mahou"))
                .andExpect(jsonPath("$.amount").value(120.5))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2030-05-10T12:00:00Z"))
                .andExpect(jsonPath("$.createdByName").value("First owner Last owner"))
                // Entered by hand, not by receiving a purchase order
                .andExpect(jsonPath("$.purchaseOrderId").value(nullValue()))
                .andReturn();
        // Money always goes out with two decimals
        assertEquals(true, result.getResponse().getContentAsString().contains("\"amount\":120.50"));
        assertEquals(true, result.getResponse().getContentAsString().contains("\"purchaseOrderId\":null"));

        Expense saved = expenseRepository.findById(longAt(result, "$.id")).orElseThrow();
        assertEquals(new BigDecimal("120.50"), saved.getAmount());
        assertEquals(owner.getId(), saved.getCreatedBy().getId());
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postJson("/api/expenses", """
                        { "description": " ", "supplier": "%s", "amount": 0, "notes": "%s" }
                        """.formatted("s".repeat(101), "n".repeat(501))).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.date").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.category").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.description").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.supplier").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").value("must be greater than 0"))
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.notes").exists());

        mockMvc.perform(postJson("/api/expenses", body(venue.getId(), "2030-05-10", "CASH", "12.345")
                        .replace("\"Ice\"", "\"" + "d".repeat(151) + "\"")).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value(containsString("numeric value out of bounds")))
                .andExpect(jsonPath("$.fieldErrors.description").exists());
        mockMvc.perform(postJson("/api/expenses", body(venue.getId(), "2030-05-10", "CASH", "-3")).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").value("must be greater than 0"));
        mockMvc.perform(postJson("/api/expenses", body(venue.getId(), "2030-05-10", "CHEQUE", "3")).with(as(owner)))
                .andExpect(status().isBadRequest());
        assertEquals(0, expenseRepository.count());
    }

    @Test
    void theDateCannotBeInTheFutureOfTheVenuesTimeZone() throws Exception {
        mockMvc.perform(postJson("/api/expenses", body(venue.getId(), "2030-05-11", "CARD", "10")).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The expense date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));

        // At 12:00 UTC it is already 02:00 on Saturday in Kiritimati (UTC+14)
        venue.setTimeZone("Pacific/Kiritimati");
        establishmentRepository.save(venue);
        Long id = longAt(mockMvc.perform(postJson("/api/expenses", body(venue.getId(), "2030-05-11", "CARD", "10"))
                        .with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(putJson("/api/expenses/{id}", body(null, "2030-05-12", "CARD", "10"), id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The expense date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    void anUpdateChangesEverythingButTheEstablishment() throws Exception {
        Long id = create(venue, "2030-05-09", "CASH", "10");
        Establishment otherVenue = createEstablishment(owner);

        mockMvc.perform(putJson("/api/expenses/{id}", """
                        { "establishmentId": %d, "date": "2030-05-08", "category": "MAINTENANCE",
                          "description": "Boiler repair", "supplier": null, "amount": 99.99,
                          "paymentMethod": "TRANSFER", "notes": "Invoice 42" }
                        """.formatted(otherVenue.getId()), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.date").value("2030-05-08"))
                .andExpect(jsonPath("$.category").value("MAINTENANCE"))
                .andExpect(jsonPath("$.description").value("Boiler repair"))
                .andExpect(jsonPath("$.supplier").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(99.99))
                .andExpect(jsonPath("$.paymentMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.notes").value("Invoice 42"))
                .andExpect(jsonPath("$.createdByName").value("First owner Last owner"))
                .andExpect(jsonPath("$.purchaseOrderId").value(nullValue()));

        // Without establishmentId too
        mockMvc.perform(putJson("/api/expenses/{id}", body(null, "2030-05-08", "OTHER", "5"), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()));
        assertEquals(venue.getId(), expenseRepository.findEstablishmentIdById(id).orElseThrow());
    }

    @Test
    void listsNewestFirstWithOptionalFilters() throws Exception {
        Long first = create(venue, "2030-05-08", "CASH", "10");
        Long second = create(venue, "2030-05-09", "CARD", "20");
        Long third = create(venue, "2030-05-09", "CASH", "30");
        Long rent = createWithCategory(venue, "2030-05-01", "RENT");
        create(createEstablishment(createOwner("other-owner")), "2030-05-09", "CASH", "40");

        String list = "/api/expenses/establishment/" + venue.getId();
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(third.intValue(), second.intValue(), first.intValue(), rent.intValue())))
                .andExpect(jsonPath("$[*].purchaseOrderId", contains(nullValue(), nullValue(), nullValue(), nullValue())));
        mockMvc.perform(getJson(list + "?from=2030-05-09").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(third.intValue(), second.intValue())));
        mockMvc.perform(getJson(list + "?to=2030-05-08").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(first.intValue(), rent.intValue())));
        mockMvc.perform(getJson(list + "?from=2030-05-01&to=2030-05-31&category=RENT").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(rent.intValue())));
        mockMvc.perform(getJson(list + "?category=TAXES").with(as(owner)))
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(getJson(list + "?from=2030-05-10&to=2030-05-09").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
        mockMvc.perform(getJson(list + "?category=FOOD").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'FOOD' for parameter 'category'"));
    }

    @Test
    void aListingCoversAtMost366Days() throws Exception {
        Long friday = create(venue, "2030-05-10", "CARD", "10");
        Long firstDay = create(venue, "2029-05-10", "CARD", "20");
        Long dayBefore = create(venue, "2029-05-09", "CARD", "30");
        String list = "/api/expenses/establishment/" + venue.getId();

        // Without dates: the 366 days up to today, Friday 10 May 2030 in Madrid
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), firstDay.intValue())));
        // With only one date, the other is 365 days away from it
        mockMvc.perform(getJson(list + "?from=2029-05-09").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        mockMvc.perform(getJson(list + "?to=2029-05-31&category=SUPPLIES").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        // Both: at most 366 days, as in the reports
        mockMvc.perform(getJson(list + "?from=2029-05-09&to=2030-05-10").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));
    }

    @Test
    void deletesAnExpense() throws Exception {
        Long id = create(venue, "2030-05-09", "CARD", "10");

        mockMvc.perform(deleteJson("/api/expenses/{id}", id).with(as(owner)))
                .andExpect(status().isNoContent());
        assertFalse(expenseRepository.existsById(id));

        mockMvc.perform(deleteJson("/api/expenses/{id}", id).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Expense not found with id " + id));
    }

    private Long create(Establishment establishment, String date, String paymentMethod, String amount) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/expenses", body(establishment.getId(), date, paymentMethod, amount))
                        .with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long createWithCategory(Establishment establishment, String date, String category) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/expenses", body(establishment.getId(), date, "TRANSFER", "800")
                                .replace("SUPPLIES", category))
                        .with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private static String body(Long establishmentId, String date, String paymentMethod, String amount) {
        return """
                { "establishmentId": %s, "date": "%s", "category": "SUPPLIES", "description": "Ice",
                  "amount": %s, "paymentMethod": "%s" }
                """.formatted(establishmentId, date, amount, paymentMethod);
    }
}

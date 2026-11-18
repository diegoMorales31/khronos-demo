package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Purchase orders: drafts that can be edited, each transition and the ones that aren't allowed, and
 * receiving, which records the stock, updates costs and can create an expense, all or nothing.
 * "Now" is Friday 10 May 2030 at 12:00 UTC, 14:00 in Madrid.
 */
class PurchaseOrderTests extends IntegrationTest {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Establishment venue;
    private Long mahou;
    private Long beer;
    private Long rum;
    private Long lemons;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        mahou = supplier(venue, "Mahou");
        beer = product(venue, "Barril Mahou", "KEG", "90.00");
        rum = product(venue, "Ron", "BOTTLE", "10.00");
        lemons = product(venue, "Limones", "KG", null);
    }

    @Test
    void createsADraftWithItsLinesAndTotal() throws Exception {
        MvcResult result = mockMvc.perform(postJson("/api/purchase-orders", """
                        { "establishmentId": %d, "supplierId": %d, "expectedDate": "2030-05-14", "notes": "  Antes de las 10 ",
                          "lines": [ { "productId": %d, "quantity": 2, "unitCost": 91.5 },
                                     { "productId": %d, "quantity": 1.5 } ] }
                        """.formatted(venue.getId(), mahou, beer, lemons)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.supplierId").value(mahou))
                .andExpect(jsonPath("$.supplierName").value("Mahou"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.orderDate").value(nullValue()))
                .andExpect(jsonPath("$.expectedDate").value("2030-05-14"))
                .andExpect(jsonPath("$.receivedDate").value(nullValue()))
                .andExpect(jsonPath("$.notes").value("Antes de las 10"))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].id").isNumber())
                .andExpect(jsonPath("$.lines[0].productId").value(beer))
                .andExpect(jsonPath("$.lines[0].productName").value("Barril Mahou"))
                .andExpect(jsonPath("$.lines[0].unit").value("KEG"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2.0))
                .andExpect(jsonPath("$.lines[0].unitCost").value(91.5))
                .andExpect(jsonPath("$.lines[0].receivedQuantity").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(183.0))
                .andExpect(jsonPath("$.lines[1].productName").value("Limones"))
                .andExpect(jsonPath("$.lines[1].unitCost").value(nullValue()))
                .andExpect(jsonPath("$.lines[1].lineTotal").value(nullValue()))
                // Lines without a cost don't count
                .andExpect(jsonPath("$.total").value(183.0))
                .andExpect(jsonPath("$.expenseId").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2030-05-10T12:00:00Z"))
                .andExpect(jsonPath("$.createdByName").value("First owner Last owner"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"quantity\":1.500") && body.contains("\"total\":183.00"), body);

        // With no cost anywhere the total is unknown
        Long noCost = createOrder(lineOf(lemons, "2", null));
        mockMvc.perform(getJson("/api/purchase-orders/{id}", noCost).with(as(owner)))
                .andExpect(jsonPath("$.total").value(nullValue()));
        // Nothing moves until it is received
        assertEquals(0, stockMovementRepository.count());
    }

    @Test
    void listsNewestFirstByStatusAndDayOfCreation() throws Exception {
        clock.setNow(Instant.parse("2030-05-01T10:00:00Z"));
        Long first = createOrder(lineOf(rum, "1", null));
        // 23:30 UTC on the 2nd is already the 3rd in Madrid
        clock.setNow(Instant.parse("2030-05-02T23:30:00Z"));
        Long second = createOrder(lineOf(rum, "1", null));
        clock.setNow(Instant.parse("2030-05-05T10:00:00Z"));
        Long third = createOrder(lineOf(rum, "1", null));
        transition(third, "order").andExpect(status().isOk());
        Establishment otherVenue = createEstablishment(owner);
        Long otherSupplier = supplier(otherVenue, "Makro");
        Long otherRum = product(otherVenue, "Ron", "BOTTLE", null);
        mockMvc.perform(postJson("/api/purchase-orders", """
                        { "establishmentId": %d, "supplierId": %d, "lines": [ { "productId": %d, "quantity": 1 } ] }
                        """.formatted(otherVenue.getId(), otherSupplier, otherRum)).with(as(owner)))
                .andExpect(status().isCreated());

        String url = "/api/purchase-orders/establishment/" + venue.getId();
        mockMvc.perform(getJson(url).with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(third.intValue(), second.intValue(), first.intValue())));
        mockMvc.perform(getJson(url + "?status=DRAFT").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(second.intValue(), first.intValue())));
        mockMvc.perform(getJson(url + "?from=2030-05-03&to=2030-05-05").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(third.intValue(), second.intValue())));
        mockMvc.perform(getJson(url + "?to=2030-05-02").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(first.intValue())));
        mockMvc.perform(getJson(url + "?status=SENT").with(as(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(getJson(url + "?from=2030-05-06&to=2030-05-05").with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aListingCoversAtMost366Days() throws Exception {
        // 22:30 UTC on 8 May 2029 is already the 9th in Madrid
        clock.setNow(Instant.parse("2029-05-08T22:30:00Z"));
        Long dayBefore = createOrder(lineOf(rum, "1", null));
        clock.setNow(Instant.parse("2029-05-10T10:00:00Z"));
        Long firstDay = createOrder(lineOf(rum, "1", null));
        transition(firstDay, "order").andExpect(status().isOk());
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        Long friday = createOrder(lineOf(rum, "1", null));
        String list = "/api/purchase-orders/establishment/" + venue.getId();

        // Without dates: the 366 days of creation up to today, Friday 10 May 2030 in Madrid
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), firstDay.intValue())));
        // With only one date, the other is 365 days away from it; the status still applies
        mockMvc.perform(getJson(list + "?from=2029-05-09").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        mockMvc.perform(getJson(list + "?to=2029-05-31&status=DRAFT").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(dayBefore.intValue())));
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

        // An admin listing an establishment that doesn't exist: 404 without dates, nothing with them
        User admin = createAdmin("admin");
        Long missing = venue.getId() + 1000;
        mockMvc.perform(getJson("/api/purchase-orders/establishment/{id}", missing).with(as(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(getJson("/api/purchase-orders/establishment/{id}?to=2030-05-10", missing).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyADraftCanBeEditedOrDeleted() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(rum, "6", null));
        Long otherSupplier = supplier(venue, "Bodegas");

        // A product kept on the order, one dropped and one added
        mockMvc.perform(putJson("/api/purchase-orders/{id}", """
                        { "establishmentId": 999999, "supplierId": %d, "expectedDate": null,
                          "lines": [ { "productId": %d, "quantity": 12, "unitCost": 9.5 },
                                     { "productId": %d, "quantity": 4 } ] }
                        """.formatted(otherSupplier, rum, lemons), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.supplierName").value("Bodegas"))
                .andExpect(jsonPath("$.lines[*].productName", contains("Ron", "Limones")))
                .andExpect(jsonPath("$.lines[0].quantity").value(12.0))
                .andExpect(jsonPath("$.total").value(114.0));

        transition(id, "order").andExpect(status().isOk());
        mockMvc.perform(putJson("/api/purchase-orders/{id}", orderBody(mahou, lineOf(rum, "1", null)), id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft purchase order can be edited; this one is ORDERED"))
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_WRONG_STATUS"));
        mockMvc.perform(deleteJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft purchase order can be deleted; this one is ORDERED"));

        Long draft = createOrder(lineOf(beer, "1", null));
        mockMvc.perform(deleteJson("/api/purchase-orders/{id}", draft).with(as(owner)))
                .andExpect(status().isNoContent());
        assertFalse(purchaseOrderRepository.existsById(draft));
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from purchase_order_lines", Integer.class));
    }

    @Test
    void movesThroughItsStatusesAndRefusesTheOtherMoves() throws Exception {
        Long id = createOrder(lineOf(rum, "1", null));
        // 23:30 UTC is already the 11th in Madrid
        clock.setNow(Instant.parse("2030-05-10T23:30:00Z"));
        transition(id, "order")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.orderDate").value("2030-05-11"));
        wrongStatus(transition(id, "order"), "Only a draft purchase order can be ordered; this one is ORDERED");
        transition(id, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderDate").value("2030-05-11"));
        wrongStatus(transition(id, "cancel"), "Only a draft or ordered purchase order can be cancelled; this one is CANCELLED");
        wrongStatus(transition(id, "order"), "Only a draft purchase order can be ordered; this one is CANCELLED");
        wrongStatus(receive(id, receiptOf(id, "1"), false, null),
                "Only an ordered purchase order can be received; this one is CANCELLED");

        Long draft = createOrder(lineOf(rum, "1", null));
        wrongStatus(receive(draft, receiptOf(draft, "1"), false, null),
                "Only an ordered purchase order can be received; this one is DRAFT");
        transition(draft, "cancel").andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderDate").value(nullValue()));

        Long received = createOrder(lineOf(rum, "1", null));
        transition(received, "order");
        receive(received, receiptOf(received, "1"), false, null).andExpect(status().isOk());
        wrongStatus(transition(received, "cancel"), "Only a draft or ordered purchase order can be cancelled; this one is RECEIVED");
        wrongStatus(transition(received, "order"), "Only a draft purchase order can be ordered; this one is RECEIVED");
        wrongStatus(receive(received, receiptOf(received, "1"), false, null),
                "Only an ordered purchase order can be received; this one is RECEIVED");
        assertEquals(1, stockMovementRepository.count());
    }

    @Test
    void receivingRecordsWhatCameInAndUpdatesTheCosts() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(rum, "6", "10"), lineOf(lemons, "3", null));
        transition(id, "order");
        String lines = """
                [ { "lineId": %d, "receivedQuantity": 2, "unitCost": 92.5 },
                  { "lineId": %d, "receivedQuantity": 0 },
                  { "lineId": %d, "receivedQuantity": 2.5 } ]
                """.formatted(lineId(id, 0), lineId(id, 1), lineId(id, 2));

        receive(id, lines, false, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedDate").value("2030-05-10"))
                .andExpect(jsonPath("$.lines[*].receivedQuantity", contains(2.0, 0.0, 2.5)))
                .andExpect(jsonPath("$.lines[*].unitCost", contains(92.5, 10.0, null)))
                // The lines count what was received
                .andExpect(jsonPath("$.lines[*].lineTotal", contains(185.0, 0.0, null)))
                .andExpect(jsonPath("$.total").value(185.0))
                .andExpect(jsonPath("$.expenseId").value(nullValue()));

        // One purchase per line that came in, linked to the order and valued at the line's cost
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?type=PURCHASE", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].productName", contains("Limones", "Barril Mahou")))
                .andExpect(jsonPath("$[*].purchaseOrderId", contains(id.intValue(), id.intValue())))
                .andExpect(jsonPath("$[*].quantity", contains(2.5, 2.0)))
                .andExpect(jsonPath("$[*].unitCost", contains(null, 92.5)))
                .andExpect(jsonPath("$[*].date", contains("2030-05-10", "2030-05-10")));
        // The beer's cost is the one received; the rum, which didn't come, keeps its own
        mockMvc.perform(getJson("/api/products/{id}", beer).with(as(owner)))
                .andExpect(jsonPath("$.unitCost").value(92.5))
                .andExpect(jsonPath("$.currentStock").value(2.0));
        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.unitCost").value(10.0))
                .andExpect(jsonPath("$.currentStock").value(0.0));
        assertEquals(0, expenseRepository.count());
    }

    @Test
    void aLineWithoutACostTakesTheProductsCostIntoTheTotalAndTheExpense() throws Exception {
        Long tonic = product(venue, "Tónica", "UNIT", "3.00");
        // 5 rums at 10.00; 4 tonics without a cost, whose product costs 3.00; lemons without a cost anywhere
        Long id = createOrder(lineOf(rum, "5", "10"), lineOf(tonic, "4", null), lineOf(lemons, "2", null));
        transition(id, "order")
                .andExpect(jsonPath("$.lines[*].unitCost", contains(10.0, null, null)))
                .andExpect(jsonPath("$.total").value(50.0));

        // The tonics are valued at their product's cost, which the line keeps, so they count in the total
        Long expenseId = longAt(receive(id, receiptOf(id, "5"), true, "CARD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[*].unitCost", contains(10.0, 3.0, null)))
                .andExpect(jsonPath("$.lines[*].lineTotal", contains(50.0, 12.0, null)))
                .andExpect(jsonPath("$.total").value(62.0))
                .andReturn(), "$.expenseId");

        // The expense and the movements count the same 62.00
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].id").value(expenseId))
                .andExpect(jsonPath("$[0].amount").value(62.0));
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[*].productName", contains("Limones", "Tónica", "Ron")))
                .andExpect(jsonPath("$[*].unitCost", contains(null, 3.0, 10.0)))
                .andExpect(jsonPath("$[*].value", contains(null, 12.0, 50.0)))
                .andExpect(jsonPath("$[*].stockAfter", contains(2.0, 4.0, 5.0)));

        // The line keeps the cost it was received at when the product's changes later
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Tónica", "category": "OTHER", "unit": "UNIT", "unitCost": 3.5 }
                        """, tonic).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.lines[1].unitCost").value(3.0))
                .andExpect(jsonPath("$.total").value(62.0));
        assertEquals(new BigDecimal("62.00"), expenseRepository.findById(expenseId).orElseThrow().getAmount());
    }

    @Test
    void receivingCanCreateTheExpense() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(lemons, "3", "1.25"));
        transition(id, "order");
        MvcResult result = receive(id, """
                [ { "lineId": %d, "receivedQuantity": 2 }, { "lineId": %d, "receivedQuantity": 2.5 } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), true, "CARD", "2030-05-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(183.13))
                .andExpect(jsonPath("$.expenseId").isNumber())
                .andReturn();
        Long expenseId = longAt(result, "$.expenseId");

        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(expenseId))
                .andExpect(jsonPath("$[0].date").value("2030-05-09"))
                .andExpect(jsonPath("$[0].category").value("SUPPLIES"))
                .andExpect(jsonPath("$[0].description").value("Pedido #" + id))
                .andExpect(jsonPath("$[0].supplier").value("Mahou"))
                .andExpect(jsonPath("$[0].amount").value(183.13))
                .andExpect(jsonPath("$[0].paymentMethod").value("CARD"))
                .andExpect(jsonPath("$[0].createdByName").value("First owner Last owner"));
    }

    @Test
    void aCashExpenseOnAClosedDayReceivesNothing() throws Exception {
        Long closing = longAt(mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "2030-05-09", "openingFloat": 100, "cashSales": 0,
                          "cardSales": 0, "countedCash": 100 }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(put("/api/cash-closings/{id}/close", closing).with(as(owner))).andExpect(status().isOk());
        Long id = createOrder(lineOf(beer, "2", "95"), lineOf(rum, "6", null));
        transition(id, "order");
        // Both lines come in with a new cost, which would become each product's cost
        String lines = """
                [ { "lineId": %d, "receivedQuantity": 2, "unitCost": 96 }, { "lineId": %d, "receivedQuantity": 6, "unitCost": 11 } ]
                """.formatted(lineId(id, 0), lineId(id, 1));

        // The expense's own 409, with its code and the closed day, as when it is entered by hand
        receive(id, lines, true, "CASH", "2030-05-09")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cash closing of 2030-05-09 is closed; reopen it to add,"
                        + " change or delete cash expenses of that day"))
                .andExpect(jsonPath("$.code").value("CASH_DAY_CLOSED"))
                .andExpect(jsonPath("$.details.date").value("2030-05-09"));

        // Nothing at all was received: the order, its lines and the products are as they were
        mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.orderDate").value("2030-05-10"))
                .andExpect(jsonPath("$.receivedDate").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].receivedQuantity").value(nullValue()))
                .andExpect(jsonPath("$.lines[0].unitCost").value(95.0))
                .andExpect(jsonPath("$.lines[1].receivedQuantity").value(nullValue()))
                .andExpect(jsonPath("$.lines[1].unitCost").value(nullValue()))
                .andExpect(jsonPath("$.total").value(190.0))
                .andExpect(jsonPath("$.expenseId").value(nullValue()));
        assertEquals(0, stockMovementRepository.count());
        assertEquals(0, expenseRepository.count());
        assertEquals(new BigDecimal("90.00"), productRepository.findById(beer).orElseThrow().getUnitCost());
        assertEquals(new BigDecimal("10.00"), productRepository.findById(rum).orElseThrow().getUnitCost());
        mockMvc.perform(getJson("/api/products/{id}", beer).with(as(owner)))
                .andExpect(jsonPath("$.currentStock").value(0.0));

        // Paid by card, the closed day doesn't matter
        receive(id, lines, true, "CARD", "2030-05-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.expenseId").isNumber());
        assertEquals(new BigDecimal("96.00"), productRepository.findById(beer).orElseThrow().getUnitCost());
    }

    @Test
    void anExpenseNeedsAPaymentMethodAndATotal() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(lemons, "3", null));
        transition(id, "order");
        mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", """
                        { "lines": [ { "lineId": %d, "receivedQuantity": 2 }, { "lineId": %d, "receivedQuantity": 3 } ],
                          "createExpense": true }
                        """.formatted(lineId(id, 0), lineId(id, 1)), id).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.paymentMethod").value("must not be null when createExpense is true"));

        // Only the lemons came, and they have no cost: the total is unknown
        String onlyLemons = """
                [ { "lineId": %d, "receivedQuantity": 0 }, { "lineId": %d, "receivedQuantity": 3 } ]
                """.formatted(lineId(id, 0), lineId(id, 1));
        receive(id, onlyLemons, true, "CARD")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NO_TOTAL"))
                .andExpect(jsonPath("$.message").value("The received purchase order has no total to record as an"
                        + " expense; give its lines a unit cost or receive it without an expense"));
        // A total of 0 is refused as well
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 0 }, { "lineId": %d, "receivedQuantity": 3, "unitCost": 0 } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), true, "CARD")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NO_TOTAL"));
        assertEquals(0, stockMovementRepository.count());

        // Without an expense it can be received
        receive(id, onlyLemons, false, null).andExpect(status().isOk());
    }

    @Test
    void aReceiptThatWouldLeaveAStockTooLargeToKeepReceivesNothing() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(rum, "1", "10"));
        transition(id, "order");
        // The rum's stock is already 999999999999, written straight to the ledger
        jdbcTemplate.update("""
                insert into stock_movements (id, establishment_id, product_id, type, quantity, date, stock_after,
                                             created_at, created_by_name)
                select -n, ?, ?, 'PURCHASE', 999999999.999, date '2030-05-01', 0, now(), 'Seed'
                from generate_series(1, 1000) n
                """, venue.getId(), rum);

        receive(id, receiptOf(id, "2"), true, "CARD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors['lines[1].receivedQuantity']").value("would leave a stock of"
                        + " 1000000000000.000; a product's stock can be at most 999999999999.999 either way"))
                .andExpect(jsonPath("$.fieldErrors['lines[0].receivedQuantity']").doesNotExist());

        // Nothing at all was received
        mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.lines[*].receivedQuantity", contains(nullValue(), nullValue())))
                .andExpect(jsonPath("$.expenseId").value(nullValue()));
        assertEquals(1000, stockMovementRepository.count());
        assertEquals(0, expenseRepository.count());
    }

    @Test
    void everyLineMustBeSentOnceAndSomethingMustArrive() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"), lineOf(rum, "6", null));
        Long other = createOrder(lineOf(rum, "1", null));
        transition(id, "order");

        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 2 } ]
                """.formatted(lineId(id, 0)), false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Every line of the purchase order must be sent; line "
                        + lineId(id, 1) + " is missing"));
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 2 }, { "lineId": %d, "receivedQuantity": 6 },
                  { "lineId": %d, "receivedQuantity": 1 } ]
                """.formatted(lineId(id, 0), lineId(id, 1), lineId(other, 0)), false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Line " + lineId(other, 0) + " is not a line of this purchase order"));
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 2 }, { "lineId": %d, "receivedQuantity": 6 } ]
                """.formatted(lineId(id, 0), lineId(id, 0)), false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lines").value("must not contain the same line twice"));
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 0 }, { "lineId": %d, "receivedQuantity": 0 } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOTHING_RECEIVED"))
                .andExpect(jsonPath("$.message").value("Nothing was received; cancel the purchase order instead"));
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": -1 }, { "lineId": %d } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), false, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['lines[0].receivedQuantity']").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors['lines[1].receivedQuantity']").value("must not be null"));
        mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", "{ \"lines\": [] }", id).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lines").value("size must be between 1 and 200"))
                .andExpect(jsonPath("$.fieldErrors.createExpense").value("must not be null"));
        assertEquals(0, stockMovementRepository.count());
    }

    @Test
    void theReceivedDayIsTodayByDefaultAndNeverLater() throws Exception {
        Long id = createOrder(lineOf(rum, "1", null));
        transition(id, "order");
        receive(id, receiptOf(id, "1"), false, null, "2030-05-11")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The received date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
        // Nothing was received
        assertEquals(0, stockMovementRepository.count());
        receive(id, receiptOf(id, "1"), false, null, "2030-05-08")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedDate").value("2030-05-08"));
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].date").value("2030-05-08"));
    }

    @Test
    void inactiveProductsCannotBeOrderedOrReceived() throws Exception {
        setActive(lemons, false);
        mockMvc.perform(postJson("/api/purchase-orders", orderBody(mahou, lineOf(lemons, "1", null))).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        setActive(lemons, true);

        Long id = createOrder(lineOf(lemons, "1", null), lineOf(rum, "1", null));
        setActive(lemons, false);
        mockMvc.perform(put("/api/purchase-orders/{id}/order", id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'Limones' is inactive; activate it first"));
        setActive(lemons, true);
        transition(id, "order").andExpect(status().isOk());

        // Deactivated once ordered: it can't come in, but a line of it that didn't come doesn't matter
        setActive(lemons, false);
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 1 }, { "lineId": %d, "receivedQuantity": 1 } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), false, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        assertEquals(0, stockMovementRepository.count());
        receive(id, """
                [ { "lineId": %d, "receivedQuantity": 0 }, { "lineId": %d, "receivedQuantity": 1 } ]
                """.formatted(lineId(id, 0), lineId(id, 1)), false, null)
                .andExpect(status().isOk());
    }

    @Test
    void theSupplierAndProductsMustBeOfTheOrdersEstablishment() throws Exception {
        // The owner has both venues, so the access check passes and the service refuses it
        Establishment otherVenue = createEstablishment(owner);
        Long makro = supplier(otherVenue, "Makro");
        Long otherRum = product(otherVenue, "Ron", "BOTTLE", null);
        mockMvc.perform(postJson("/api/purchase-orders", orderBody(makro, lineOf(rum, "1", null))).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The supplier belongs to another establishment"))
                .andExpect(jsonPath("$.code").value("OTHER_ESTABLISHMENT"));
        mockMvc.perform(postJson("/api/purchase-orders", orderBody(mahou, lineOf(rum, "1", null), lineOf(otherRum, "1", null)))
                        .with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'Ron' belongs to another establishment"));
        Long id = createOrder(lineOf(rum, "1", null));
        mockMvc.perform(putJson("/api/purchase-orders/{id}", orderBody(mahou, lineOf(otherRum, "1", null)), id).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OTHER_ESTABLISHMENT"));
        assertEquals(1, purchaseOrderRepository.count());
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postJson("/api/purchase-orders", """
                        { "notes": "%s", "lines": [] }
                        """.formatted("n".repeat(501))).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.supplierId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.notes").value("size must be between 0 and 500"))
                .andExpect(jsonPath("$.fieldErrors.lines").value("size must be between 1 and 200"));
        mockMvc.perform(postJson("/api/purchase-orders", orderBody(mahou, lineOf(rum, "0", "-1"), lineOf(rum, "1", null)))
                        .with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['lines[0].quantity']").value("must be greater than 0"))
                .andExpect(jsonPath("$.fieldErrors['lines[0].unitCost']").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors.lines").value("must not contain the same product twice"));
        assertEquals(0, purchaseOrderRepository.count());
    }

    @Test
    void deletingTheExpenseKeepsTheOrderAndItsStock() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"));
        transition(id, "order");
        Long expenseId = longAt(receive(id, receiptOf(id, "2"), true, "TRANSFER").andReturn(), "$.expenseId");

        mockMvc.perform(deleteJson("/api/expenses/{id}", expenseId).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.expenseId").value(nullValue()))
                .andExpect(jsonPath("$.total").value(180.0));
        assertEquals(1, stockMovementRepository.count());
        assertNull(purchaseOrderRepository.findById(id).orElseThrow().getExpense());
    }

    @Test
    void theExpenseOfAnOrderSaysSoAndCannotBeEdited() throws Exception {
        Long id = createOrder(lineOf(beer, "2", "90"));
        transition(id, "order");
        Long expenseId = longAt(receive(id, receiptOf(id, "2"), true, "TRANSFER").andReturn(), "$.expenseId");
        Long byHand = longAt(mockMvc.perform(postJson("/api/expenses", expenseBody("2030-05-09", "40")).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        // The listing says which order each expense came from
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(expenseId.intValue(), byHand.intValue())))
                .andExpect(jsonPath("$[0].purchaseOrderId").value(id))
                .andExpect(jsonPath("$[1].purchaseOrderId").value(nullValue()));

        // Its amount, day and category are the order's: it can't be edited, not even to the same values
        mockMvc.perform(putJson("/api/expenses/{id}", expenseBody("2030-05-10", "180"), expenseId).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPENSE_FROM_PURCHASE_ORDER"))
                .andExpect(jsonPath("$.details.purchaseOrderId").value(id.toString()))
                .andExpect(jsonPath("$.message").value("This expense was created when purchase order #" + id
                        + " was received and cannot be edited; to correct it, delete it and enter it again"));
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].amount").value(180.0))
                .andExpect(jsonPath("$[0].paymentMethod").value("TRANSFER"));
        // One entered by hand still can be
        mockMvc.perform(putJson("/api/expenses/{id}", expenseBody("2030-05-09", "45"), byHand).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(45.0))
                .andExpect(jsonPath("$.purchaseOrderId").value(nullValue()));

        // Deleting it is allowed, and then the order has no expense
        mockMvc.perform(deleteJson("/api/expenses/{id}", expenseId).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andExpect(jsonPath("$.expenseId").value(nullValue()));
    }

    @Test
    void theExpenseListingFindsTheirOrdersInOneQuery() throws Exception {
        Long first = createOrder(lineOf(beer, "1", "90"));
        transition(first, "order");
        receive(first, receiptOf(first, "1"), true, "CARD").andExpect(status().isOk());
        long one = expenseListingStatements();

        for (int i = 0; i < 4; i++) {
            Long id = createOrder(lineOf(rum, "1", "10"));
            transition(id, "order");
            receive(id, receiptOf(id, "1"), true, "CARD", "2030-05-0" + (i + 1)).andExpect(status().isOk());
        }
        long five = expenseListingStatements();

        assertTrue(one > 0, "statistics must be enabled in the test profile");
        assertEquals(one, five);
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].purchaseOrderId").value(first));
    }

    @Test
    void suppliersAndProductsOnOrdersAreKept() throws Exception {
        Long draftOnly = product(venue, "Vermut", "BOTTLE", null);
        Long id = createOrder(lineOf(draftOnly, "1", null));
        transition(id, "cancel");

        mockMvc.perform(deleteJson("/api/suppliers/{id}", mahou).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete a supplier that has purchase orders"))
                .andExpect(jsonPath("$.code").value("SUPPLIER_HAS_ORDERS"));
        mockMvc.perform(deleteJson("/api/products/{id}", draftOnly).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_HISTORY"));
        assertTrue(supplierRepository.existsById(mahou));
        assertTrue(productRepository.existsById(draftOnly));
    }

    @Test
    void aMissingOrderIsNotFoundForAnAdmin() throws Exception {
        User admin = createAdmin("admin");
        mockMvc.perform(getJson("/api/purchase-orders/{id}", 999999).with(as(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PurchaseOrder not found with id 999999"));
        mockMvc.perform(put("/api/purchase-orders/{id}/order", 999999).with(as(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", """
                        { "lines": [ { "lineId": 1, "receivedQuantity": 1 } ], "createExpense": false }
                        """, 999999).with(as(admin)))
                .andExpect(status().isNotFound());
    }

    // ---- Helpers ----

    private void wrongStatus(ResultActions actions, String message) throws Exception {
        actions.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_WRONG_STATUS"));
    }

    private ResultActions transition(Long id, String transition) throws Exception {
        return mockMvc.perform(put("/api/purchase-orders/{id}/" + transition, id).with(as(owner)));
    }

    private ResultActions receive(Long id, String lines, boolean createExpense, String paymentMethod) throws Exception {
        return receive(id, lines, createExpense, paymentMethod, null);
    }

    private ResultActions receive(Long id, String lines, boolean createExpense, String paymentMethod, String date)
            throws Exception {
        return mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", """
                { "receivedDate": %s, "lines": %s, "createExpense": %s, "paymentMethod": %s }
                """.formatted(date != null ? "\"" + date + "\"" : null, lines, createExpense,
                paymentMethod != null ? "\"" + paymentMethod + "\"" : null), id).with(as(owner)));
    }

    // Every line of the order received in full: the quantity given for its first line, the rest as ordered
    private String receiptOf(Long id, String firstQuantity) throws Exception {
        String json = mockMvc.perform(getJson("/api/purchase-orders/{id}", id).with(as(owner)))
                .andReturn().getResponse().getContentAsString();
        int count = com.jayway.jsonpath.JsonPath.<Integer>read(json, "$.lines.length()");
        StringBuilder lines = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            Number lineId = com.jayway.jsonpath.JsonPath.read(json, "$.lines[" + i + "].id");
            Object quantity = i == 0 ? firstQuantity : com.jayway.jsonpath.JsonPath.read(json, "$.lines[" + i + "].quantity");
            lines.append(i > 0 ? "," : "").append("{ \"lineId\": ").append(lineId).append(", \"receivedQuantity\": ")
                    .append(quantity).append(" }");
        }
        return lines.append("]").toString();
    }

    private Long lineId(Long orderId, int index) throws Exception {
        return longAt(mockMvc.perform(getJson("/api/purchase-orders/{id}", orderId).with(as(owner))).andReturn(),
                "$.lines[" + index + "].id");
    }

    private Long createOrder(String... lines) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/purchase-orders", orderBody(mahou, lines)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private String orderBody(Long supplierId, String... lines) {
        return """
                { "establishmentId": %d, "supplierId": %d, "lines": [ %s ] }
                """.formatted(venue.getId(), supplierId, String.join(",", lines));
    }

    private static String lineOf(Long productId, String quantity, String unitCost) {
        return "{ \"productId\": %d, \"quantity\": %s, \"unitCost\": %s }".formatted(productId, quantity, unitCost);
    }

    private long expenseListingStatements() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private String expenseBody(String date, String amount) {
        return """
                { "establishmentId": %d, "date": "%s", "category": "SUPPLIES", "description": "Pedido",
                  "amount": %s, "paymentMethod": "TRANSFER" }
                """.formatted(venue.getId(), date, amount);
    }

    private void setActive(Long productId, boolean active) throws Exception {
        mockMvc.perform(putJson("/api/products/{id}/active", "{ \"active\": " + active + " }", productId).with(as(owner)))
                .andExpect(status().isOk());
    }

    private Long supplier(Establishment establishment, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(establishment.getId(), name)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long product(Establishment establishment, String name, String unit, String unitCost) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "OTHER", "unit": "%s", "unitCost": %s }
                        """.formatted(establishment.getId(), name, unit, unitCost)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}

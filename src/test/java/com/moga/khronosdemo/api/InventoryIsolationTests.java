package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Product;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import com.moga.khronosdemo.repositories.PurchaseOrderRepository;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The inventory is only for the establishment's owner and admins. A worker gets 403 everywhere, even for
 * their own venue and before their request is validated, and owner A gets 403 for everything of owner B's
 * venue, also when one of B's suppliers, products or order lines is named in the body of a request about
 * A's own venue. Nothing of B's is created, changed or shown.
 */
class InventoryIsolationTests extends IntegrationTest {

    private static final String RANGE = "?from=2030-05-01&to=2030-05-31";

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    private User ownerA;
    private Establishment venueA;
    private Worker workerA;
    private Long supplierA;
    private Long productA;
    private Long draftA;
    private Long orderedA;
    private User ownerB;
    private Establishment venueB;
    private Worker workerB;
    private Long supplierB;
    private Long productB;
    private Long draftB;
    private Long orderedB;
    private Long lineOfB;

    @BeforeEach
    void setUp() throws Exception {
        ownerA = createOwner("owner-a");
        venueA = createEstablishment(ownerA);
        workerA = createWorker(venueA, "worker-a");
        ownerB = createOwner("owner-b");
        venueB = createEstablishment(ownerB);
        workerB = createWorker(venueB, "worker-b");
        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));

        supplierA = supplier(ownerA, venueA, "Mahou");
        productA = product(ownerA, venueA, "Ron A");
        draftA = order(ownerA, venueA, supplierA, productA);
        orderedA = order(ownerA, venueA, supplierA, productA);
        mockMvc.perform(put("/api/purchase-orders/{id}/order", orderedA).with(as(ownerA))).andExpect(status().isOk());

        supplierB = supplier(ownerB, venueB, "Makro");
        productB = product(ownerB, venueB, "Ron B");
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 6 }
                        """.formatted(productB)).with(as(ownerB)))
                .andExpect(status().isCreated());
        draftB = order(ownerB, venueB, supplierB, productB);
        orderedB = order(ownerB, venueB, supplierB, productB);
        lineOfB = longAt(mockMvc.perform(put("/api/purchase-orders/{id}/order", orderedB).with(as(ownerB)))
                .andExpect(status().isOk())
                .andReturn(), "$.lines[0].id");
    }

    @Test
    void aWorkerCannotReachAnyOfItEvenForTheirOwnVenue() throws Exception {
        // Each caller gets fresh requests: a request builder keeps what was added to it
        for (MockHttpServletRequestBuilder request : everyEndpointOfB()) {
            expectForbidden(as(workerB), request);
        }
        for (MockHttpServletRequestBuilder request : everyEndpointOfB()) {
            expectForbidden(as(workerA), request);
        }
        assertNothingOfBChanged();
    }

    @Test
    void aWorkerIsForbiddenBeforeTheirRequestIsValidated() throws Exception {
        // Every one of these answers 400 to owner A, the venue's owner: a body or parameters that fail
        // validation. A worker of that venue gets 403 for each, before anything is read
        for (MockHttpServletRequestBuilder request : invalidRequestsAboutA()) {
            expectForbidden(as(workerA), request);
        }
        for (MockHttpServletRequestBuilder request : invalidRequestsAboutA()) {
            mockMvc.perform(request.with(as(ownerA))).andExpect(status().isBadRequest());
        }

        // Without a token it is still 401
        mockMvc.perform(postJson("/api/stock-movements", "{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertNothingOfBChanged();
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from stock_movements where establishment_id = ?",
                Integer.class, venueA.getId()));
        assertEquals(PurchaseOrderStatus.ORDERED, purchaseOrderRepository.findById(orderedA).orElseThrow().getStatus());
    }

    @Test
    void anOwnerCannotReachAnotherOwnersInventory() throws Exception {
        for (MockHttpServletRequestBuilder request : everyEndpointOfB()) {
            expectForbidden(as(ownerA), request);
        }
        assertNothingOfBChanged();
    }

    @Test
    void anOwnerCannotNameAnotherOwnersSupplierProductOrLineInABody() throws Exception {
        RequestPostProcessor a = as(ownerA);
        expectForbidden(a, postJson("/api/products", """
                { "establishmentId": %d, "name": "Vodka", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": %d }
                """.formatted(venueA.getId(), supplierB)));
        expectForbidden(a, putJson("/api/products/{id}", """
                { "name": "Ron A", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": %d }
                """.formatted(supplierB), productA));
        expectForbidden(a, postJson("/api/stock-counts", count(venueA, productA, productB)));
        expectForbidden(a, postJson("/api/stock-counts", count(venueB, productA)));
        expectForbidden(a, postJson("/api/purchase-orders", orderBody(venueA, supplierB, productA)));
        expectForbidden(a, postJson("/api/purchase-orders", orderBody(venueA, supplierA, productA, productB)));
        expectForbidden(a, putJson("/api/purchase-orders/{id}", orderBody(venueA, supplierB, productA), draftA));
        expectForbidden(a, putJson("/api/purchase-orders/{id}", orderBody(venueA, supplierA, productB), draftA));

        // A line of B's order is not a line of A's order, and says nothing about it
        mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", """
                        { "lines": [ { "lineId": %d, "receivedQuantity": 1 } ], "createExpense": false }
                        """.formatted(lineOfB), orderedA).with(a))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Line " + lineOfB + " is not a line of this purchase order"));
        // Filtering A's movements by B's product shows nothing
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?productId={p}", venueA.getId(), productB).with(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // An update ignores the establishment in its body, so A's records stay in A's venue
        mockMvc.perform(putJson("/api/suppliers/{id}", """
                        { "establishmentId": %d, "name": "Mahou" }
                        """.formatted(venueB.getId()), supplierA).with(a))
                .andExpect(jsonPath("$.establishmentId").value(venueA.getId()));
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "establishmentId": %d, "name": "Ron A", "category": "SPIRITS", "unit": "BOTTLE" }
                        """.formatted(venueB.getId()), productA).with(a))
                .andExpect(jsonPath("$.establishmentId").value(venueA.getId()));
        mockMvc.perform(putJson("/api/purchase-orders/{id}", orderBody(venueB, supplierA, productA), draftA).with(a))
                .andExpect(jsonPath("$.establishmentId").value(venueA.getId()));

        assertNothingOfBChanged();
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from stock_movements where establishment_id = ?",
                Integer.class, venueA.getId()));
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from purchase_orders where establishment_id = ?",
                Integer.class, venueA.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from products where establishment_id = ?",
                Integer.class, venueA.getId()));
    }

    @Test
    void aMissingRecordIsForbiddenToAnOwnerAndNotFoundForAnAdmin() throws Exception {
        long missing = 999999;
        expectForbidden(as(ownerA), getJson("/api/products/{id}", missing));
        expectForbidden(as(ownerA), deleteJson("/api/suppliers/{id}", missing));
        expectForbidden(as(ownerA), getJson("/api/purchase-orders/{id}", missing));
        expectForbidden(as(ownerA), postJson("/api/stock-movements", """
                { "productId": %d, "type": "PURCHASE", "quantity": 1 }
                """.formatted(missing)));
        User admin = createAdmin("admin");
        mockMvc.perform(getJson("/api/products/{id}", missing).with(as(admin))).andExpect(status().isNotFound());
        mockMvc.perform(deleteJson("/api/suppliers/{id}", missing).with(as(admin))).andExpect(status().isNotFound());
        mockMvc.perform(getJson("/api/purchase-orders/{id}", missing).with(as(admin))).andExpect(status().isNotFound());
    }

    @Test
    void anAdminReachesEveryVenue() throws Exception {
        User admin = createAdmin("admin");
        mockMvc.perform(getJson("/api/suppliers/establishment/{id}", venueB.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(getJson("/api/products/establishment/{id}", venueB.getId()).with(as(admin)))
                .andExpect(jsonPath("$[0].currentStock").value(6.0));
        mockMvc.perform(getJson("/api/purchase-orders/establishment/{id}", venueB.getId()).with(as(admin)))
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory" + RANGE, venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/establishment/{id}/inventory.csv", venueB.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "CONSUMPTION", "quantity": 1 }
                        """.formatted(productB)).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByName").value("First admin Last admin"))
                .andExpect(jsonPath("$.stockAfter").value(5.0));
    }

    // Every endpoint of this release, aimed at venue B and its records
    private List<MockHttpServletRequestBuilder> everyEndpointOfB() {
        return List.of(
                getJson("/api/suppliers/establishment/{id}", venueB.getId()),
                postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "Hijack" }
                        """.formatted(venueB.getId())),
                putJson("/api/suppliers/{id}", "{ \"name\": \"Hijack\" }", supplierB),
                deleteJson("/api/suppliers/{id}", supplierB),
                getJson("/api/products/establishment/{id}?includeInactive=true", venueB.getId()),
                getJson("/api/products/{id}", productB),
                postJson("/api/products", """
                        { "establishmentId": %d, "name": "Hijack", "category": "OTHER", "unit": "UNIT" }
                        """.formatted(venueB.getId())),
                putJson("/api/products/{id}", """
                        { "name": "Hijack", "category": "OTHER", "unit": "UNIT" }
                        """, productB),
                putJson("/api/products/{id}/active", "{ \"active\": false }", productB),
                deleteJson("/api/products/{id}", productB),
                postJson("/api/stock-movements", """
                        { "productId": %d, "type": "WASTE", "quantity": 6, "reason": "Hijack" }
                        """.formatted(productB)),
                getJson("/api/stock-movements/establishment/{id}", venueB.getId()),
                postJson("/api/stock-counts", count(venueB, productB)),
                getJson("/api/purchase-orders/establishment/{id}", venueB.getId()),
                getJson("/api/purchase-orders/{id}", draftB),
                postJson("/api/purchase-orders", orderBody(venueB, supplierB, productB)),
                putJson("/api/purchase-orders/{id}", orderBody(venueB, supplierB, productB), draftB),
                deleteJson("/api/purchase-orders/{id}", draftB),
                putJson("/api/purchase-orders/{id}/order", "{}", draftB),
                putJson("/api/purchase-orders/{id}/cancel", "{}", orderedB),
                putJson("/api/purchase-orders/{id}/receive", """
                        { "lines": [ { "lineId": %d, "receivedQuantity": 1 } ], "createExpense": true,
                          "paymentMethod": "CARD" }
                        """.formatted(lineOfB), orderedB),
                getJson("/api/reports/establishment/{id}/inventory" + RANGE, venueB.getId()),
                get("/api/reports/establishment/{id}/inventory.csv", venueB.getId()),
                get("/api/reports/establishment/{id}/stock-movements.csv" + RANGE, venueB.getId()));
    }

    // Requests about venue A and its records, on every path of this release, that fail validation
    private List<MockHttpServletRequestBuilder> invalidRequestsAboutA() {
        return List.of(
                getJson("/api/suppliers/establishment/{id}", "nope"),
                postJson("/api/suppliers", "{}"),
                putJson("/api/suppliers/{id}", "{ \"name\": \"\" }", supplierA),
                deleteJson("/api/suppliers/{id}", "nope"),
                getJson("/api/products/establishment/{id}?includeInactive=maybe", venueA.getId()),
                getJson("/api/products/{id}", "nope"),
                postJson("/api/products", "{}"),
                putJson("/api/products/{id}", "{}", productA),
                putJson("/api/products/{id}/active", "{}", productA),
                postJson("/api/stock-movements", """
                        { "productId": %d, "type": "COUNT", "quantity": 1 }
                        """.formatted(productA)),
                getJson("/api/stock-movements/establishment/{id}?type=THEFT", venueA.getId()),
                getJson("/api/stock-movements/establishment/{id}?from=2030-05-10&to=2030-05-01", venueA.getId()),
                postJson("/api/stock-counts", "{}"),
                getJson("/api/purchase-orders/establishment/{id}?status=SENT", venueA.getId()),
                getJson("/api/purchase-orders/{id}", "nope"),
                postJson("/api/purchase-orders", "{}"),
                putJson("/api/purchase-orders/{id}", "{}", draftA),
                putJson("/api/purchase-orders/{id}/order", "{}", "nope"),
                putJson("/api/purchase-orders/{id}/receive", "{ \"lines\": [] }", orderedA),
                getJson("/api/reports/establishment/{id}/inventory", venueA.getId()),
                getJson("/api/reports/establishment/{id}/inventory?from=2030-05-01", venueA.getId()),
                get("/api/reports/establishment/{id}/inventory.csv", "nope"),
                get("/api/reports/establishment/{id}/stock-movements.csv?from=2030-05-01&to=2031-06-01", venueA.getId()));
    }

    private void expectForbidden(RequestPostProcessor caller, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(caller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    private void assertNothingOfBChanged() {
        assertEquals("Makro", supplierRepository.findById(supplierB).orElseThrow().getName());
        Product product = productRepository.findById(productB).orElseThrow();
        assertEquals("Ron B", product.getName());
        assertTrue(product.isActive());
        assertEquals(1, supplierRepository.findByEstablishment(venueB.getId()).size());
        assertEquals(1, productRepository.findByEstablishment(venueB.getId(), true).size());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from stock_movements where establishment_id = ?",
                Integer.class, venueB.getId()));
        assertEquals(PurchaseOrderStatus.DRAFT, purchaseOrderRepository.findById(draftB).orElseThrow().getStatus());
        assertEquals(PurchaseOrderStatus.ORDERED, purchaseOrderRepository.findById(orderedB).orElseThrow().getStatus());
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from purchase_orders where establishment_id = ?",
                Integer.class, venueB.getId()));
        assertEquals(0, expenseRepository.count());
    }

    private Long supplier(User owner, Establishment venue, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(venue.getId(), name)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long product(User owner, Establishment venue, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "SPIRITS", "unit": "BOTTLE", "unitCost": 10 }
                        """.formatted(venue.getId(), name)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long order(User owner, Establishment venue, Long supplier, Long product) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/purchase-orders", orderBody(venue, supplier, product)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private static String orderBody(Establishment venue, Long supplier, Long... products) {
        StringBuilder lines = new StringBuilder();
        for (Long product : products) {
            lines.append(lines.isEmpty() ? "" : ",").append("{ \"productId\": ").append(product)
                    .append(", \"quantity\": 2, \"unitCost\": 10 }");
        }
        return """
                { "establishmentId": %d, "supplierId": %d, "lines": [ %s ] }
                """.formatted(venue.getId(), supplier, lines);
    }

    private static String count(Establishment venue, Long... products) {
        StringBuilder items = new StringBuilder();
        for (Long product : products) {
            items.append(items.isEmpty() ? "" : ",").append("{ \"productId\": ").append(product)
                    .append(", \"countedQuantity\": 0 }");
        }
        return """
                { "establishmentId": %d, "items": [ %s ] }
                """.formatted(venue.getId(), items);
    }
}

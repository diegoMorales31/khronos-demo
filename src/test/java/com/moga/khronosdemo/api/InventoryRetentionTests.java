package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock movements and purchase orders are an establishment's inventory record, so an establishment or
 * an owner that has them can't be deleted, through the API or straight in the database. Without them,
 * the products and suppliers go with their establishment. Deleting the account that recorded something
 * keeps that person's name on it.
 */
class InventoryRetentionTests extends IntegrationTest {

    private User admin;
    private User owner;
    private Establishment venueWithMovements;
    private Establishment venueWithOrder;
    private Long rum;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        admin = createAdmin("admin");
        owner = createOwner("owner");
        venueWithMovements = createEstablishment(owner);
        venueWithOrder = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));

        rum = product(venueWithMovements, "Ron");
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 6 }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isCreated());
        // Only a draft order, with no movements
        Long supplier = supplier(venueWithOrder, "Mahou");
        orderId = longAt(mockMvc.perform(postJson("/api/purchase-orders", """
                        { "establishmentId": %d, "supplierId": %d, "lines": [ { "productId": %d, "quantity": 1 } ] }
                        """.formatted(venueWithOrder.getId(), supplier, product(venueWithOrder, "Ginebra"))).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    @Test
    void anEstablishmentWithStockMovementsOrPurchaseOrdersCannotBeDeleted() throws Exception {
        String message = "Cannot delete an establishment that has stock movements or purchase orders; keep it to keep"
                + " its inventory records";
        for (Establishment venue : new Establishment[]{venueWithMovements, venueWithOrder}) {
            mockMvc.perform(deleteJson("/api/establishments/{id}", venue.getId()).with(as(admin)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(message));
            assertTrue(establishmentRepository.existsById(venue.getId()));
        }
        assertEverythingKept();
    }

    @Test
    void anOwnerWhoseEstablishmentsHaveInventoryRecordsCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete an owner whose establishments have stock movements"
                        + " or purchase orders; keep the owner to keep those inventory records"));
        assertTrue(userRepository.existsById(owner.getId()));
        assertEverythingKept();
    }

    @Test
    void withoutThemProductsAndSuppliersGoWithTheirEstablishment() throws Exception {
        User other = createOwner("other");
        Establishment venue = createEstablishment(other);
        Long supplier = supplier(venue, "Makro");
        Long product = longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Tónica", "category": "SOFT_DRINKS", "unit": "UNIT", "supplierId": %d }
                        """.formatted(venue.getId(), supplier)).with(as(other)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(deleteJson("/api/establishments/{id}", venue.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertFalse(productRepository.existsById(product));
        assertFalse(supplierRepository.existsById(supplier));

        // Once its draft is deleted, the second venue can go too; the first venue's movements still keep the owner
        mockMvc.perform(deleteJson("/api/purchase-orders/{id}", orderId).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/establishments/{id}", venueWithOrder.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void theDatabaseRefusesToDeleteThemOrWhatTheyReferTo() {
        assertEquals("r", onDelete("fk_stock_movements_establishment"));
        assertEquals("r", onDelete("fk_stock_movements_product"));
        assertEquals("r", onDelete("fk_stock_movements_purchase_order"));
        assertEquals("r", onDelete("fk_purchase_orders_establishment"));
        assertEquals("r", onDelete("fk_purchase_orders_supplier"));
        assertEquals("r", onDelete("fk_purchase_order_lines_product"));
        assertEquals("c", onDelete("fk_purchase_order_lines_purchase_order"));
        assertEquals("c", onDelete("fk_products_establishment"));
        assertEquals("c", onDelete("fk_suppliers_establishment"));
        assertEquals("n", onDelete("fk_products_supplier"));
        assertEquals("n", onDelete("fk_purchase_orders_expense"));
        assertEquals("n", onDelete("fk_stock_movements_created_by"));
        assertEquals("n", onDelete("fk_purchase_orders_created_by"));

        DataIntegrityViolationException movementsVenue = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from establishments where id = ?", venueWithMovements.getId()));
        assertTrue(movementsVenue.getMostSpecificCause().getMessage().contains("fk_stock_movements"));
        DataIntegrityViolationException orderVenue = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from establishments where id = ?", venueWithOrder.getId()));
        assertTrue(orderVenue.getMostSpecificCause().getMessage().contains("fk_purchase_orders_establishment"));
        DataIntegrityViolationException product = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from products where id = ?", rum));
        assertTrue(product.getMostSpecificCause().getMessage().contains("fk_stock_movements_product"));
        assertEverythingKept();
    }

    @Test
    void deletingTheAuthorKeepsTheirName() throws Exception {
        // Both venues move to a new owner, then the old owner's account is deleted
        User newOwner = createOwner("new-owner");
        for (Establishment venue : new Establishment[]{venueWithMovements, venueWithOrder}) {
            mockMvc.perform(putJson("/api/establishments/{id}", """
                            { "establishmentName": "Venue", "establishmentAddress": "%s", "ownerId": %d }
                            """.formatted("New address " + venue.getId(), newOwner.getId()), venue.getId()).with(as(admin)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}", venueWithMovements.getId()).with(as(newOwner)))
                .andExpect(jsonPath("$[*].createdByName", contains("First owner Last owner")));
        mockMvc.perform(getJson("/api/purchase-orders/{id}", orderId).with(as(newOwner)))
                .andExpect(jsonPath("$.createdByName").value("First owner Last owner"));
        assertNull(stockMovementRepository.findAll().get(0).getCreatedBy());
    }

    private void assertEverythingKept() {
        assertEquals(1, stockMovementRepository.count());
        assertTrue(productRepository.existsById(rum));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from purchase_orders", Integer.class));
    }

    private String onDelete(String constraint) {
        return jdbcTemplate.queryForObject("select confdeltype::text from pg_constraint where conname = ?",
                String.class, constraint);
    }

    private Long supplier(Establishment venue, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(venue.getId(), name)).with(as(venue.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long product(Establishment venue, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "SPIRITS", "unit": "BOTTLE" }
                        """.formatted(venue.getId(), name)).with(as(venue.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}

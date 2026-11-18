package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.ProductUnit;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Products: create, list, edit, activate and delete, names unique per establishment whatever their case,
 * and the figures worked out from the stock movements (stock, its value and whether it is low).
 */
class ProductTests extends IntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private Establishment venue;
    private Long mahou;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        mahou = supplier(venue, "Mahou");
    }

    @Test
    void createsAProductWithItsFigures() throws Exception {
        MvcResult result = mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "  Mahou Clásica barril ", "category": "BEER", "unit": "KEG",
                          "minStock": 2, "unitCost": 95.5, "supplierId": %d }
                        """.formatted(venue.getId(), mahou)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.name").value("Mahou Clásica barril"))
                .andExpect(jsonPath("$.category").value("BEER"))
                .andExpect(jsonPath("$.unit").value("KEG"))
                .andExpect(jsonPath("$.minStock").value(2.0))
                .andExpect(jsonPath("$.unitCost").value(95.5))
                .andExpect(jsonPath("$.supplierId").value(mahou))
                .andExpect(jsonPath("$.supplierName").value("Mahou"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.currentStock").value(0.0))
                .andExpect(jsonPath("$.stockValue").value(0.0))
                // No stock is at or below a minimum of 2
                .andExpect(jsonPath("$.isLowStock").value(true))
                .andReturn();
        // Quantities go out with three decimals and money with two
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"minStock\":2.000"), body);
        assertTrue(body.contains("\"unitCost\":95.50"), body);
        assertTrue(body.contains("\"currentStock\":0.000"), body);
        assertTrue(body.contains("\"stockValue\":0.00"), body);

        Long id = longAt(result, "$.id");
        movement(id, "PURCHASE", "3", null, null);
        movement(id, "CONSUMPTION", "0.5", null, null);
        mockMvc.perform(getJson("/api/products/{id}", id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(2.5))
                .andExpect(jsonPath("$.stockValue").value(238.75))
                .andExpect(jsonPath("$.isLowStock").value(false));
    }

    @Test
    void stockValueIsNullWithoutACostAndZeroWithNoStockOnHand() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.stockValue").value(nullValue()))
                .andExpect(jsonPath("$.minStock").value(nullValue()))
                .andExpect(jsonPath("$.isLowStock").value(false));

        Long gin = product(venue, "Ginebra", "SPIRITS", "BOTTLE", null, "12.40");
        movement(gin, "CONSUMPTION", "2", null, null);
        mockMvc.perform(getJson("/api/products/{id}", gin).with(as(owner)))
                .andExpect(jsonPath("$.currentStock").value(-2.0))
                .andExpect(jsonPath("$.stockValue").value(0.0));
    }

    @Test
    void listsByCategoryThenNameAndLeavesOutInactiveOnesUnlessAsked() throws Exception {
        Long other = product(venue, "Hielo", "OTHER", "KG", null, null);
        product(venue, "vermut", "WINE", "BOTTLE", null, null);
        product(venue, "Albariño", "WINE", "BOTTLE", null, null);
        product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        Long beer = product(venue, "Caña", "BEER", "KEG", "1", null);
        product(createEstablishment(owner), "Tónica", "SOFT_DRINKS", "UNIT", null, null);
        movement(beer, "PURCHASE", "4", null, null);
        mockMvc.perform(putJson("/api/products/{id}/active", """
                        { "active": false }
                        """, other).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Categories in the order the contract lists them, then names whatever their case
        mockMvc.perform(getJson("/api/products/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Ron", "Caña", "Albariño", "vermut")))
                .andExpect(jsonPath("$[1].currentStock").value(4.0))
                .andExpect(jsonPath("$[0].currentStock").value(0.0));
        mockMvc.perform(getJson("/api/products/establishment/{id}?includeInactive=true", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[*].name", contains("Ron", "Caña", "Albariño", "vermut", "Hielo")))
                .andExpect(jsonPath("$[4].isActive").value(false));
    }

    @Test
    void namesAreUniqueWithinAnEstablishmentWhateverTheirCase() throws Exception {
        Long rum = product(venue, "Ron Barceló", "SPIRITS", "BOTTLE", null, null);
        Long gin = product(venue, "Ginebra", "SPIRITS", "BOTTLE", null, null);

        mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "RON BARCELÓ", "category": "SPIRITS", "unit": "BOTTLE" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A product named 'RON BARCELÓ' already exists at this establishment"))
                .andExpect(jsonPath("$.code").value("PRODUCT_NAME_TAKEN"));
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "ron barceló", "category": "SPIRITS", "unit": "BOTTLE" }
                        """, gin).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A product named 'ron barceló' already exists at this establishment"));
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "RON Barceló", "category": "SPIRITS", "unit": "BOTTLE" }
                        """, rum).with(as(owner)))
                .andExpect(status().isOk());
        product(createEstablishment(owner), "Ron Barceló", "SPIRITS", "BOTTLE", null, null);

        DataIntegrityViolationException duplicate = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        insert into products (id, establishment_id, name, category, unit) values (-1, ?, 'GINEBRA', 'OTHER', 'UNIT')
                        """, venue.getId()));
        assertTrue(duplicate.getMostSpecificCause().getMessage().contains("ux_products_establishment_name"));
    }

    @Test
    void editsAProductAndKeepsItInItsEstablishment() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", "2", "10");
        Long bodegas = supplier(venue, "Bodegas");
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "establishmentId": %d, "name": "Ron añejo", "category": "OTHER", "unit": "UNIT",
                          "minStock": null, "unitCost": 11.25, "supplierId": %d }
                        """.formatted(createEstablishment(owner).getId(), bodegas), rum).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.name").value("Ron añejo"))
                .andExpect(jsonPath("$.category").value("OTHER"))
                // No movements yet, so the unit can still change
                .andExpect(jsonPath("$.unit").value("UNIT"))
                .andExpect(jsonPath("$.minStock").value(nullValue()))
                .andExpect(jsonPath("$.unitCost").value(11.25))
                .andExpect(jsonPath("$.supplierName").value("Bodegas"));
    }

    @Test
    void theUnitCannotChangeOnceTheProductHasMovements() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        movement(rum, "PURCHASE", "6", null, null);

        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Ron", "category": "SPIRITS", "unit": "BOX" }
                        """, rum).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The unit of a product with stock movements or purchase order"
                        + " lines cannot be changed"))
                .andExpect(jsonPath("$.code").value("PRODUCT_UNIT_LOCKED"));
        // Everything else can
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Ron blanco", "category": "SPIRITS", "unit": "BOTTLE", "minStock": 1.5 }
                        """, rum).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ron blanco"))
                .andExpect(jsonPath("$.currentStock").value(6.0));
    }

    @Test
    void theUnitCannotChangeOnceTheProductIsOnAPurchaseOrderWhateverItsStatus() throws Exception {
        Long gin = product(venue, "Ginebra", "SPIRITS", "BOTTLE", null, null);
        // 10 bottles on a draft: once in kg, receiving it would bring in 10 kg
        Long order = longAt(mockMvc.perform(postJson("/api/purchase-orders", """
                        { "establishmentId": %d, "supplierId": %d, "lines": [ { "productId": %d, "quantity": 10 } ] }
                        """.formatted(venue.getId(), mahou, gin)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        String inKilos = """
                { "name": "Ginebra", "category": "SPIRITS", "unit": "KG" }
                """;

        for (String transition : List.of("order", "cancel")) {
            mockMvc.perform(putJson("/api/products/{id}", inKilos, gin).with(as(owner)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_UNIT_LOCKED"));
            // Ordered, then cancelled: the line is still there
            mockMvc.perform(put("/api/purchase-orders/{id}/" + transition, order).with(as(owner)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(putJson("/api/products/{id}", inKilos, gin).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The unit of a product with stock movements or purchase order"
                        + " lines cannot be changed"));
        assertEquals(ProductUnit.BOTTLE, productRepository.findById(gin).orElseThrow().getUnit());
        // Nothing else is locked
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Ginebra rosa", "category": "SPIRITS", "unit": "BOTTLE" }
                        """, gin).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ginebra rosa"));
    }

    @Test
    void theSupplierMustBeOfTheSameEstablishment() throws Exception {
        Establishment otherVenue = createEstablishment(owner);
        Long otherSupplier = supplier(otherVenue, "Makro");
        // The owner has both venues, so the access check passes and the service refuses it
        mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Ron", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": %d }
                        """.formatted(venue.getId(), otherSupplier)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The supplier belongs to another establishment"))
                .andExpect(jsonPath("$.code").value("OTHER_ESTABLISHMENT"));
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Ron", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": %d }
                        """.formatted(otherSupplier), rum).with(as(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Vodka", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": 999999 }
                        """.formatted(venue.getId())).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound());
        assertNull(productRepository.findWithSupplierById(rum).orElseThrow().getSupplier());
    }

    @Test
    void deletingASupplierClearsItFromItsProducts() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        mockMvc.perform(putJson("/api/products/{id}", """
                        { "name": "Ron", "category": "SPIRITS", "unit": "BOTTLE", "supplierId": %d }
                        """.formatted(mahou), rum).with(as(owner)))
                .andExpect(jsonPath("$.supplierId").value(mahou));

        mockMvc.perform(deleteJson("/api/suppliers/{id}", mahou).with(as(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.supplierId").value(nullValue()))
                .andExpect(jsonPath("$.supplierName").value(nullValue()));
    }

    @Test
    void anInactiveProductTakesNoMovementsUntilItIsActiveAgain() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        movement(rum, "PURCHASE", "2", null, null);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(putJson("/api/products/{id}/active", """
                            { "active": false }
                            """, rum).with(as(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false))
                    .andExpect(jsonPath("$.currentStock").value(2.0));
        }
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "CONSUMPTION", "quantity": 1 }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'Ron' is inactive; activate it first"))
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));

        mockMvc.perform(putJson("/api/products/{id}/active", """
                        { "active": true }
                        """, rum).with(as(owner)))
                .andExpect(jsonPath("$.isActive").value(true));
        movement(rum, "CONSUMPTION", "1", null, null);
        mockMvc.perform(putJson("/api/products/{id}/active", "{}", rum).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.active").value("must not be null"));
    }

    @Test
    void onlyAProductWithoutMovementsCanBeDeleted() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        Long gin = product(venue, "Ginebra", "SPIRITS", "BOTTLE", null, null);
        movement(rum, "ADJUSTMENT", "1", null, "Botella encontrada");

        mockMvc.perform(deleteJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete a product that has stock movements or purchase"
                        + " order lines; deactivate it instead (PUT /api/products/" + rum + "/active) to keep its history"))
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_HISTORY"));
        mockMvc.perform(deleteJson("/api/products/{id}", gin).with(as(owner)))
                .andExpect(status().isNoContent());
        assertTrue(productRepository.existsById(rum));
        assertFalse(productRepository.existsById(gin));
        mockMvc.perform(getJson("/api/products/{id}", gin).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postJson("/api/products", """
                        { "name": " ", "minStock": -1, "unitCost": 1.005 }
                        """).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.category").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.unit").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.minStock").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors.unitCost").exists());
        mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Ron", "category": "SPIRITS", "unit": "BOTTLE", "minStock": 1.0005 }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minStock").exists());
        mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Ron", "category": "LIQUOR", "unit": "BOTTLE" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isBadRequest());
        assertEquals(0, productRepository.count());
    }

    @Test
    void theListingRunsTheSameQueriesHoweverManyProductsAndMovements() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, "10");
        movement(rum, "PURCHASE", "1", null, null);
        long few = listingStatements();

        for (int i = 0; i < 6; i++) {
            Long extra = product(venue, "Extra " + i, "OTHER", "UNIT", "1", "1");
            movement(extra, "PURCHASE", "2", "1.50", null);
            movement(extra, "CONSUMPTION", "1", null, null);
            mockMvc.perform(putJson("/api/products/{id}", """
                            { "name": "Extra %d", "category": "OTHER", "unit": "UNIT", "supplierId": %d }
                            """.formatted(i, mahou), extra).with(as(owner)))
                    .andExpect(status().isOk());
        }
        long many = listingStatements();

        assertTrue(few > 0, "statistics must be enabled in the test profile");
        assertEquals(few, many);
    }

    @Test
    void theStockOfAnEstablishmentIsAddedUpFromAnIndexAlone() throws Exception {
        Long rum = product(venue, "Ron", "SPIRITS", "BOTTLE", null, null);
        Establishment otherVenue = createEstablishment(owner);
        Long gin = product(otherVenue, "Ginebra", "SPIRITS", "BOTTLE", null, null);
        // 2000 movements of two venues, written straight to the ledger; vacuum marks their pages visible
        jdbcTemplate.update("""
                insert into stock_movements (id, establishment_id, product_id, type, quantity, date, stock_after,
                                             created_at, created_by_name)
                select -n, case when n % 2 = 0 then ? else ? end, case when n % 2 = 0 then ? else ? end,
                       'PURCHASE', 1, date '2030-05-01', 0, now(), 'Seed'
                from generate_series(1, 2000) n
                """, venue.getId(), otherVenue.getId(), rum, gin);
        jdbcTemplate.execute("vacuum analyze stock_movements");

        // The query StockMovementRepository.sumByEstablishment runs, planned as for a table too large to
        // read whole: it reads the V15 index alone, already in product order
        String plan = new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            jdbcTemplate.execute("set local enable_bitmapscan = off");
            return String.join("\n", jdbcTemplate.queryForList("""
                    explain select m.product_id, sum(m.quantity) from stock_movements m
                    where m.establishment_id = %d group by m.product_id
                    """.formatted(venue.getId()), String.class));
        });
        assertTrue(plan.contains("Index Only Scan using idx_stock_movements_establishment_product"), plan);
        assertEquals("CREATE INDEX idx_stock_movements_establishment_product ON public.stock_movements USING btree"
                + " (establishment_id, product_id) INCLUDE (quantity)", jdbcTemplate.queryForObject("""
                select indexdef from pg_indexes where indexname = 'idx_stock_movements_establishment_product'
                """, String.class));
    }

    private long listingStatements() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(getJson("/api/products/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private Long supplier(Establishment establishment, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(establishment.getId(), name)).with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long product(Establishment establishment, String name, String category, String unit, String minStock,
                         String unitCost) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "%s", "unit": "%s", "minStock": %s, "unitCost": %s }
                        """.formatted(establishment.getId(), name, category, unit, minStock, unitCost))
                        .with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void movement(Long productId, String type, String quantity, String unitCost, String reason) throws Exception {
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "%s", "quantity": %s, "unitCost": %s, "reason": %s }
                        """.formatted(productId, type, quantity, unitCost, reason != null ? "\"" + reason + "\"" : null))
                        .with(as(owner)))
                .andExpect(status().isCreated());
    }
}

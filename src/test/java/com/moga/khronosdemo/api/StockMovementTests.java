package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.StockMovement;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock movements entered by hand: the sign each type sets, the checks on quantities and reasons, the
 * stock after each movement, the unit cost each one is valued at, the business day in the venue's time
 * zone, and the listing. "Now" is Friday 10 May 2030 at 12:00 UTC, 14:00 in Madrid.
 */
class StockMovementTests extends IntegrationTest {

    private User owner;
    private Establishment venue;
    private Long rum;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        rum = product("Ron", "2", "10.00");
    }

    @Test
    void eachTypeSetsTheSignAndTheStockAfterFollowsTheLedger() throws Exception {
        MvcResult purchase = mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 12, "unitCost": 11.5, "date": "2030-05-09" }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productId").value(rum))
                .andExpect(jsonPath("$.productName").value("Ron"))
                .andExpect(jsonPath("$.unit").value("BOTTLE"))
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.quantity").value(12.0))
                .andExpect(jsonPath("$.unitCost").value(11.5))
                .andExpect(jsonPath("$.value").value(138.0))
                .andExpect(jsonPath("$.reason").value(nullValue()))
                .andExpect(jsonPath("$.date").value("2030-05-09"))
                .andExpect(jsonPath("$.createdAt").value("2030-05-10T12:00:00Z"))
                .andExpect(jsonPath("$.createdByName").value("First owner Last owner"))
                .andExpect(jsonPath("$.purchaseOrderId").value(nullValue()))
                .andExpect(jsonPath("$.stockAfter").value(12.0))
                .andReturn();
        String body = purchase.getResponse().getContentAsString();
        assertTrue(body.contains("\"quantity\":12.000") && body.contains("\"stockAfter\":12.000")
                && body.contains("\"value\":138.00"), body);

        // Out: the type sets the sign, and the cost is the product's, which the purchase updated
        movement("CONSUMPTION", "3", null)
                .andExpect(jsonPath("$.quantity").value(-3.0))
                .andExpect(jsonPath("$.unitCost").value(11.5))
                .andExpect(jsonPath("$.value").value(34.5))
                .andExpect(jsonPath("$.stockAfter").value(9.0));
        movement("WASTE", "1.5", "  Se cayó una botella ")
                .andExpect(jsonPath("$.quantity").value(-1.5))
                .andExpect(jsonPath("$.reason").value("Se cayó una botella"))
                .andExpect(jsonPath("$.value").value(17.25))
                .andExpect(jsonPath("$.stockAfter").value(7.5));
        movement("ADJUSTMENT", "-0.25", "Botella empezada mal anotada")
                .andExpect(jsonPath("$.quantity").value(-0.25))
                .andExpect(jsonPath("$.value").value(2.88))
                .andExpect(jsonPath("$.stockAfter").value(7.25));
        movement("ADJUSTMENT", "2", "Apareció una caja")
                .andExpect(jsonPath("$.quantity").value(2.0))
                .andExpect(jsonPath("$.stockAfter").value(9.25));
        purchase("0.75", null)
                .andExpect(jsonPath("$.stockAfter").value(10.0));

        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.currentStock").value(10.0))
                .andExpect(jsonPath("$.unitCost").value(11.5))
                .andExpect(jsonPath("$.stockValue").value(115.0));
        assertLedgerAddsUp(rum);
    }

    @Test
    void theStockCanGoBelowZeroAndIsThenLow() throws Exception {
        movement("CONSUMPTION", "3", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockAfter").value(-3.0));
        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.currentStock").value(-3.0))
                .andExpect(jsonPath("$.stockValue").value(0.0))
                .andExpect(jsonPath("$.isLowStock").value(true));
        purchase("5", null).andExpect(jsonPath("$.stockAfter").value(2.0));
        assertLedgerAddsUp(rum);
    }

    @Test
    void aProductIsLowAtOrBelowItsMinimum() throws Exception {
        purchase("3", null);
        lowStock(false);
        movement("CONSUMPTION", "0.999", null);
        lowStock(false);
        movement("CONSUMPTION", "0.001", null);
        // Exactly the minimum of 2 counts as low
        lowStock(true);
    }

    @Test
    void eachMovementKeepsTheCostItWasValuedAt() throws Exception {
        Long lemons = product("Limones", null, null);
        // Neither the purchase nor the product has a cost
        post(lemons, "PURCHASE", "4", null, null, null)
                .andExpect(jsonPath("$.unitCost").value(nullValue()))
                .andExpect(jsonPath("$.value").value(nullValue()));
        // A purchase without a cost takes the product's and leaves it as it is
        purchase("1", null).andExpect(jsonPath("$.unitCost").value(10.0));
        // A purchase with a cost becomes the product's cost; later movements are valued at it
        purchase("2", "12.40").andExpect(jsonPath("$.value").value(24.8));
        movement("WASTE", "1", "Rota").andExpect(jsonPath("$.unitCost").value(12.4));
        // Earlier movements keep the cost they were valued at
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?productId={p}&type=PURCHASE", venue.getId(), rum)
                        .with(as(owner)))
                .andExpect(jsonPath("$[*].unitCost", contains(12.4, 10.0)));
        assertEquals(new BigDecimal("12.40"), productRepository.findById(rum).orElseThrow().getUnitCost());
    }

    @Test
    void refusesACountAndQuantitiesWithTheWrongSign() throws Exception {
        invalid("""
                { "productId": %d, "type": "COUNT", "quantity": 3 }
                """, "type", "COUNT is only recorded by a stock count (POST /api/stock-counts)");
        for (String type : new String[]{"PURCHASE", "CONSUMPTION"}) {
            invalid("""
                    { "productId": %d, "type": "%s", "quantity": 0 }
                    """.formatted(rum, type), "quantity", "must be greater than 0");
            invalid("""
                    { "productId": %d, "type": "%s", "quantity": -2 }
                    """.formatted(rum, type), "quantity", "must be greater than 0");
        }
        invalid("""
                { "productId": %d, "type": "WASTE", "quantity": -1, "reason": "Rota" }
                """, "quantity", "must be greater than 0");
        invalid("""
                { "productId": %d, "type": "ADJUSTMENT", "quantity": 0, "reason": "Nada" }
                """, "quantity", "must not be 0");
        invalid("""
                { "productId": %d, "type": "PURCHASE", "quantity": 1.0001 }
                """, "quantity", "numeric value out of bounds (<9 digits>.<3 digits> expected)");
        assertEquals(0, stockMovementRepository.count());
    }

    @Test
    void wasteAndAdjustmentsNeedAReasonAndOnlyPurchasesTakeACost() throws Exception {
        invalid("""
                { "productId": %d, "type": "WASTE", "quantity": 1 }
                """, "reason", "must not be blank for WASTE and ADJUSTMENT");
        invalid("""
                { "productId": %d, "type": "ADJUSTMENT", "quantity": 1, "reason": "   " }
                """, "reason", "must not be blank for WASTE and ADJUSTMENT");
        invalid("""
                { "productId": %d, "type": "WASTE", "quantity": 1, "reason": " ab " }
                """, "reason", "size must be between 3 and 255");
        invalid("""
                { "productId": %d, "type": "WASTE", "quantity": 1, "reason": "%s" }
                """.formatted(rum, "r".repeat(256)), "reason", "size must be between 0 and 255");
        invalid("""
                { "productId": %d, "type": "CONSUMPTION", "quantity": 1, "unitCost": 2 }
                """, "unitCost", "can only be sent with a PURCHASE");
        invalid("""
                { "productId": %d, "type": "PURCHASE", "quantity": 1, "unitCost": -2 }
                """, "unitCost", "must be greater than or equal to 0");
        // A reason is optional for the other types
        movement("CONSUMPTION", "1", "ok").andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/stock-movements", "{}").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.type").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("must not be null"));
    }

    @Test
    void theDayIsTodayInTheVenuesTimeZoneAndNeverLater() throws Exception {
        // 23:30 UTC on the 10th is already the 11th in Madrid
        clock.setNow(Instant.parse("2030-05-10T23:30:00Z"));
        purchase("1", null).andExpect(jsonPath("$.date").value("2030-05-11"));
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 1, "date": "2030-05-12" }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The movement date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"))
                .andExpect(jsonPath("$.details").doesNotExist());

        // Still the 10th in the Canaries, an hour behind Madrid
        venue.setTimeZone("Atlantic/Canary");
        establishmentRepository.save(venue);
        clock.setNow(Instant.parse("2030-05-10T22:30:00Z"));
        purchase("1", null).andExpect(jsonPath("$.date").value("2030-05-10"));
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 1, "date": "2030-05-11" }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    void listsNewestFirstWithOptionalFilters() throws Exception {
        Long gin = product("Ginebra", null, null);
        Long first = id(post(rum, "PURCHASE", "6", null, null, "2030-05-01"));
        Long second = id(post(gin, "PURCHASE", "3", null, null, "2030-05-03"));
        Long third = id(post(rum, "CONSUMPTION", "1", null, null, "2030-05-03"));
        Long fourth = id(post(rum, "WASTE", "1", null, "Rota", "2030-05-08"));
        // Another venue's movements never show up
        Establishment otherVenue = createEstablishment(owner);
        Long tonic = longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "Tónica", "category": "SOFT_DRINKS", "unit": "UNIT" }
                        """.formatted(otherVenue.getId())).with(as(owner)))
                .andReturn(), "$.id");
        post(tonic, "PURCHASE", "24", null, null, null);

        String url = "/api/stock-movements/establishment/" + venue.getId();
        mockMvc.perform(getJson(url).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(fourth.intValue(), third.intValue(), second.intValue(),
                        first.intValue())));
        mockMvc.perform(getJson(url + "?from=2030-05-02&to=2030-05-03").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(third.intValue(), second.intValue())));
        mockMvc.perform(getJson(url + "?productId=" + rum + "&type=PURCHASE").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(first.intValue())));
        mockMvc.perform(getJson(url + "?type=COUNT").with(as(owner)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(getJson(url + "?productId=" + tonic).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(getJson(url + "?from=2030-05-04&to=2030-05-03").with(as(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(getJson(url + "?type=THEFT").with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aListingCoversAtMost366Days() throws Exception {
        Long friday = id(post(rum, "PURCHASE", "1", null, null, "2030-05-10"));
        Long firstDay = id(post(rum, "PURCHASE", "1", null, null, "2029-05-10"));
        Long dayBefore = id(post(rum, "CONSUMPTION", "1", null, null, "2029-05-09"));
        String list = "/api/stock-movements/establishment/" + venue.getId();

        // Without dates: the 366 days up to today, Friday 10 May 2030 in Madrid
        mockMvc.perform(getJson(list).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(friday.intValue(), firstDay.intValue())));
        // With only one date, the other is 365 days away from it; the other filters still apply
        mockMvc.perform(getJson(list + "?from=2029-05-09").with(as(owner)))
                .andExpect(jsonPath("$[*].id", contains(firstDay.intValue(), dayBefore.intValue())));
        mockMvc.perform(getJson(list + "?to=2029-05-31&type=CONSUMPTION").with(as(owner)))
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
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}", missing).with(as(admin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?from=2030-05-01", missing).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void refusesAMovementThatWouldLeaveAStockTooLargeToKeep() throws Exception {
        // 1000 purchases of 999999999.999, written straight to the ledger, leave a stock of 999999999999
        seed(rum, "PURCHASE", "999999999.999", 1000);
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "PURCHASE", "quantity": 1 }
                        """.formatted(rum)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("would leave a stock of 1000000000000.000;"
                        + " a product's stock can be at most 999999999999.999 either way"));
        // Up to the largest stock a product can have
        post(rum, "PURCHASE", "0.999", null, null, null)
                .andExpect(jsonPath("$.stockAfter").value(999999999999.999));

        // The same below zero
        Long gin = product("Ginebra", null, null);
        seed(gin, "ADJUSTMENT", "-999999999.999", 1000);
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "ADJUSTMENT", "quantity": -1, "reason": "Descuadre" }
                        """.formatted(gin)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").value("would leave a stock of -1000000000000.000;"
                        + " a product's stock can be at most 999999999999.999 either way"));
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "WASTE", "quantity": 1, "reason": "Rota" }
                        """.formatted(gin)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
        assertEquals(2001, stockMovementRepository.count());
    }

    @Test
    void aMissingProductIsNotFoundForAnAdmin() throws Exception {
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": 999999, "type": "PURCHASE", "quantity": 1 }
                        """).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id 999999"));
    }

    private void assertLedgerAddsUp(Long productId) {
        BigDecimal sum = stockMovementRepository.findAll().stream()
                .filter(movement -> movement.getProduct().getId().equals(productId))
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal last = jdbcTemplate.queryForObject("""
                select stock_after from stock_movements where product_id = ? order by created_at desc, id desc limit 1
                """, BigDecimal.class, productId);
        assertEquals(0, sum.compareTo(last), "the last stockAfter is the sum of the ledger");
    }

    // Movements of the product written straight to the ledger, without the API's checks
    private void seed(Long productId, String type, String quantity, int times) {
        jdbcTemplate.update("""
                insert into stock_movements (id, establishment_id, product_id, type, quantity, date, stock_after,
                                             created_at, created_by_name)
                select -n - ?, ?, ?, ?, ?, date '2030-05-01', 0, now(), 'Seed' from generate_series(1, ?) n
                """, stockMovementRepository.count(), venue.getId(), productId, type, new BigDecimal(quantity), times);
    }

    private void lowStock(boolean expected) throws Exception {
        mockMvc.perform(getJson("/api/products/{id}", rum).with(as(owner)))
                .andExpect(jsonPath("$.isLowStock").value(expected));
    }

    private void invalid(String body, String field, String message) throws Exception {
        String json = body.contains("%d") ? body.formatted(rum) : body;
        mockMvc.perform(postJson("/api/stock-movements", json).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors." + field).value(message));
    }

    private Long product(String name, String minStock, String unitCost) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "SPIRITS", "unit": "BOTTLE",
                          "minStock": %s, "unitCost": %s }
                        """.formatted(venue.getId(), name, minStock, unitCost)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    // A movement of the rum, without a cost
    private ResultActions movement(String type, String quantity, String reason) throws Exception {
        return post(rum, type, quantity, null, reason, null);
    }

    private ResultActions purchase(String quantity, String unitCost) throws Exception {
        return post(rum, "PURCHASE", quantity, unitCost, null, null);
    }

    private ResultActions post(Long productId, String type, String quantity, String unitCost, String reason,
                               String date) throws Exception {
        return mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "%s", "quantity": %s, "unitCost": %s, "reason": %s, "date": %s }
                        """.formatted(productId, type, quantity, unitCost, quoted(reason), quoted(date))).with(as(owner)))
                .andExpect(status().isCreated());
    }

    private static Long id(ResultActions actions) throws Exception {
        return longAt(actions.andReturn(), "$.id");
    }

    private static String quoted(String text) {
        return text != null ? "\"" + text + "\"" : null;
    }
}

package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A physical stock count records, for each product, the difference between what was counted and the
 * stock, also when it is 0, all or nothing. "Now" is Friday 10 May 2030 at 12:00 UTC, 14:00 in Madrid.
 */
class StockCountTests extends IntegrationTest {

    private User owner;
    private Establishment venue;
    private Long rum;
    private Long gin;
    private Long tonic;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T12:00:00Z"));
        rum = product(venue, "Ron", "12.00");
        gin = product(venue, "Ginebra", null);
        tonic = product(venue, "Tónica", "0.80");
        movement(rum, "PURCHASE", "10");
        movement(gin, "PURCHASE", "3");
        movement(tonic, "CONSUMPTION", "2");
    }

    @Test
    void recordsTheDifferenceOfEachProductAlsoWhenItIsZero() throws Exception {
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "notes": "  Recuento de cierre de mes ",
                          "items": [ { "productId": %d, "countedQuantity": 8.5 },
                                     { "productId": %d, "countedQuantity": 3 },
                                     { "productId": %d, "countedQuantity": 6 } ] }
                        """.formatted(venue.getId(), rum, gin, tonic)).with(as(owner)))
                .andExpect(status().isCreated())
                // In the order of the items
                .andExpect(jsonPath("$[*].productId", contains(rum.intValue(), gin.intValue(), tonic.intValue())))
                .andExpect(jsonPath("$[*].type", contains("COUNT", "COUNT", "COUNT")))
                .andExpect(jsonPath("$[*].quantity", contains(-1.5, 0.0, 8.0)))
                .andExpect(jsonPath("$[*].stockAfter", contains(8.5, 3.0, 6.0)))
                .andExpect(jsonPath("$[*].reason", contains("Recuento de cierre de mes", "Recuento de cierre de mes",
                        "Recuento de cierre de mes")))
                .andExpect(jsonPath("$[*].date", contains("2030-05-10", "2030-05-10", "2030-05-10")))
                // Valued at each product's cost; the gin has none
                .andExpect(jsonPath("$[0].value").value(18.0))
                .andExpect(jsonPath("$[1].value").doesNotExist())
                .andExpect(jsonPath("$[2].unitCost").value(0.8))
                .andExpect(jsonPath("$[2].value").value(6.4));

        // The stock is now what was counted
        mockMvc.perform(getJson("/api/products/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[*].name", contains("Ginebra", "Ron", "Tónica")))
                .andExpect(jsonPath("$[*].currentStock", contains(3.0, 8.5, 6.0)));
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?type=COUNT", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void aPastDayCanBeCountedButNotAFutureOne() throws Exception {
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "date": "2030-05-09", "items": [ { "productId": %d, "countedQuantity": 0 } ] }
                        """.formatted(venue.getId(), tonic)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].date").value("2030-05-09"))
                .andExpect(jsonPath("$[0].quantity").value(2.0))
                .andExpect(jsonPath("$[0].reason").doesNotExist());
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "date": "2030-05-11", "items": [ { "productId": %d, "countedQuantity": 0 } ] }
                        """.formatted(venue.getId(), tonic)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The count date cannot be in the future"))
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
    }

    @Test
    void refusesAProductTwice() throws Exception {
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 1 },
                                                           { "productId": %d, "countedQuantity": 2 },
                                                           { "productId": %d, "countedQuantity": 3 } ] }
                        """.formatted(venue.getId(), rum, gin, rum)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("must not contain the same product twice"));
        assertNothingCounted();
    }

    @Test
    void refusesAnotherVenuesProductAndRecordsNothing() throws Exception {
        // The owner has both venues, so the access check passes and the service refuses it
        Long otherRum = product(createEstablishment(owner), "Ron", null);
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 1 },
                                                           { "productId": %d, "countedQuantity": 2 } ] }
                        """.formatted(venue.getId(), rum, otherRum)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'Ron' belongs to another establishment"))
                .andExpect(jsonPath("$.code").value("OTHER_ESTABLISHMENT"));
        assertNothingCounted();
    }

    @Test
    void refusesAnInactiveProductAndRecordsNothing() throws Exception {
        mockMvc.perform(putJson("/api/products/{id}/active", """
                        { "active": false }
                        """, tonic).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 1 },
                                                           { "productId": %d, "countedQuantity": 2 } ] }
                        """.formatted(venue.getId(), rum, tonic)).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product 'Tónica' is inactive; activate it first"))
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        assertNothingCounted();
    }

    @Test
    void aMissingProductIsNotFoundForAnAdminAndRecordsNothing() throws Exception {
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 1 },
                                                           { "productId": 999999, "countedQuantity": 2 } ] }
                        """.formatted(venue.getId(), rum)).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id 999999"));
        assertNothingCounted();
    }

    @Test
    void refusesADifferenceTooLargeToRecordAndRecordsNothing() throws Exception {
        // The tonic's stock is -2, so counting 999999999 would record a difference of 1000000001
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 8 },
                                                           { "productId": %d, "countedQuantity": 999999999 } ] }
                        """.formatted(venue.getId(), rum, tonic)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors['items[1].countedQuantity']").value("would record a movement of"
                        + " 1000000001.000 from the current stock of -2.000; a movement can be at most 999999999.999"
                        + " either way"))
                .andExpect(jsonPath("$.fieldErrors['items[0].countedQuantity']").doesNotExist());
        assertNothingCounted();

        // The largest difference a movement can record is fine
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": 999999997.999 } ] }
                        """.formatted(venue.getId(), tonic)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].quantity").value(999999999.999))
                .andExpect(jsonPath("$[0].stockAfter").value(999999997.999));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "notes": "%s", "items": [] }
                        """.formatted("n".repeat(256))).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.notes").value("size must be between 0 and 255"))
                .andExpect(jsonPath("$.fieldErrors.items").value("size must be between 1 and 500"));
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("must not be null"));
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ { "productId": %d, "countedQuantity": -1 },
                                                           { "countedQuantity": 1.0001 } ] }
                        """.formatted(venue.getId(), rum)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].countedQuantity']").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.fieldErrors['items[1].productId']").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors['items[1].countedQuantity']").exists());
        String tooMany = IntStream.range(0, 501)
                .mapToObj(i -> "{ \"productId\": " + (i + 1) + ", \"countedQuantity\": 1 }")
                .collect(Collectors.joining(","));
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "items": [ %s ] }
                        """.formatted(venue.getId(), tooMany)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("size must be between 1 and 500"));
        assertNothingCounted();
    }

    private void assertNothingCounted() {
        assertEquals(3, stockMovementRepository.count());
    }

    private Long product(Establishment establishment, String name, String unitCost) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "SPIRITS", "unit": "BOTTLE", "unitCost": %s }
                        """.formatted(establishment.getId(), name, unitCost)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void movement(Long productId, String type, String quantity) throws Exception {
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "%s", "quantity": %s }
                        """.formatted(productId, type, quantity)).with(as(owner)))
                .andExpect(status().isCreated());
    }
}

package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The inventory report and its exports, checked against a data set worked out by hand. "Now" is Monday
 * 20 May 2030 at 10:00 UTC, 12:00 in Madrid. Every movement is valued at the cost it kept:
 * <pre>
 * Ron           (min 2, cost 10)    02/05 +10 at 10 | 03/05 waste -1 | 05/05 consumption -8        -> stock 1, low
 * Ginebra       (no cost)           04/05 +4        | 06/05 waste -0.5 | 18/05 +2 (out of range) -> stock 5.5
 * Barril Mahou  (min 1, cost 90)    10/05 +3 at 92.50 from an order | 11/05 waste -0.25 | 12/05 counted 2.5 -> 2.5
 * +Tónica       (min 12, cost 0.50) 07/05 consumption -5                                         -> stock -5, low
 * Limones       (cost 2), inactive  08/05 +3 at 2 | 09/05 waste -1                                 -> not counted now
 * </pre>
 */
class InventoryReportTests extends IntegrationTest {

    private static final String RANGE = "?from=2030-05-01&to=2030-05-15";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Establishment venue;
    private Long rum;
    private Long gin;
    private Long beer;
    private Long tonic;
    private Long lemons;
    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-20T10:00:00Z"));
        Long bebidas = supplier("@Bebidas SL");
        Long mahou = supplier("Mahou");
        rum = product("Ron", "SPIRITS", "BOTTLE", "2", "10", bebidas);
        gin = product("Ginebra", "SPIRITS", "BOTTLE", null, null, null);
        beer = product("Barril Mahou", "BEER", "KEG", "1", "90", null);
        tonic = product("+Tónica", "SOFT_DRINKS", "UNIT", "12", "0.50", null);
        lemons = product("Limones", "FOOD", "KG", null, "2", null);

        movement(rum, "PURCHASE", "10", null, "2030-05-02");
        movement(rum, "WASTE", "1", "=SUMA(A1)", "2030-05-03");
        movement(gin, "PURCHASE", "4", null, "2030-05-04");
        movement(rum, "CONSUMPTION", "8", null, "2030-05-05");
        movement(gin, "WASTE", "0.5", "Botella rota", "2030-05-06");
        movement(tonic, "CONSUMPTION", "5", null, "2030-05-07");
        movement(lemons, "PURCHASE", "3", null, "2030-05-08");
        movement(lemons, "WASTE", "1", "Pochos", "2030-05-09");

        orderId = longAt(mockMvc.perform(postJson("/api/purchase-orders", """
                        { "establishmentId": %d, "supplierId": %d,
                          "lines": [ { "productId": %d, "quantity": 3, "unitCost": 92.5 } ] }
                        """.formatted(venue.getId(), mahou, beer)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        Long lineId = longAt(mockMvc.perform(put("/api/purchase-orders/{id}/order", orderId).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn(), "$.lines[0].id");
        mockMvc.perform(putJson("/api/purchase-orders/{id}/receive", """
                        { "receivedDate": "2030-05-10", "lines": [ { "lineId": %d, "receivedQuantity": 3 } ],
                          "createExpense": false }
                        """.formatted(lineId), orderId).with(as(owner)))
                .andExpect(status().isOk());

        movement(beer, "WASTE", "0.25", "Barril pinchado", "2030-05-11");
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "date": "2030-05-12", "notes": "Recuento",
                          "items": [ { "productId": %d, "countedQuantity": 2.5 } ] }
                        """.formatted(venue.getId(), beer)).with(as(owner)))
                .andExpect(status().isCreated());
        movement(gin, "PURCHASE", "2", null, "2030-05-18");
        mockMvc.perform(putJson("/api/products/{id}/active", "{ \"active\": false }", lemons).with(as(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void reportsTheStockNowAndTheValuesOfTheRange() throws Exception {
        MvcResult result = mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2030-05-01"))
                .andExpect(jsonPath("$.to").value("2030-05-15"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                // 1 x 10 for the rum and 2.5 x 92.50 for the beer; the gin has no cost, the tonic no stock on hand
                .andExpect(jsonPath("$.stockValue").value(241.25))
                .andExpect(jsonPath("$.productsCount").value(4))
                .andExpect(jsonPath("$.lowStockCount").value(2))
                .andExpect(jsonPath("$.negativeStockCount").value(1))
                // 100.00 + 6.00 + 277.50; the gin's purchase had no cost and the one of the 18th is out of range
                .andExpect(jsonPath("$.purchasesValue").value(383.5))
                // 10.00 + 2.00 + 0.25 x 92.50 = 23.125, rounded per movement to 23.13
                .andExpect(jsonPath("$.wasteValue").value(35.13))
                .andExpect(jsonPath("$.countDifferenceValue").value(-23.13))
                .andExpect(jsonPath("$.wasteByProduct[*].productName", contains("Barril Mahou", "Ron", "Limones", "Ginebra")))
                .andExpect(jsonPath("$.wasteByProduct[*].quantity", contains(0.25, 1.0, 1.0, 0.5)))
                .andExpect(jsonPath("$.wasteByProduct[*].value", contains(23.13, 10.0, 2.0, null)))
                .andExpect(jsonPath("$.wasteByProduct[0].unit").value("KEG"))
                .andExpect(jsonPath("$.wasteByProduct[0].productId").value(beer))
                .andExpect(jsonPath("$.lowStock[*].productId", contains(rum.intValue(), tonic.intValue())))
                .andExpect(jsonPath("$.lowStock[0].productName").value("Ron"))
                .andExpect(jsonPath("$.lowStock[0].unit").value("BOTTLE"))
                .andExpect(jsonPath("$.lowStock[0].currentStock").value(1.0))
                .andExpect(jsonPath("$.lowStock[0].minStock").value(2.0))
                .andExpect(jsonPath("$.lowStock[0].supplierName").value("@Bebidas SL"))
                .andExpect(jsonPath("$.lowStock[0].supplierId").isNumber())
                .andExpect(jsonPath("$.lowStock[1].currentStock").value(-5.0))
                .andExpect(jsonPath("$.lowStock[1].supplierId").doesNotExist())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"purchasesValue\":383.50") && body.contains("\"currentStock\":1.000"), body);

        // A shorter range changes the values but not the stock
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory?from=2030-05-10&to=2030-05-12", venue.getId())
                        .with(as(owner)))
                .andExpect(jsonPath("$.stockValue").value(241.25))
                .andExpect(jsonPath("$.purchasesValue").value(277.5))
                .andExpect(jsonPath("$.wasteValue").value(23.13))
                .andExpect(jsonPath("$.wasteByProduct", hasSize(1)));
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory?from=2030-05-16&to=2030-05-17", venue.getId())
                        .with(as(owner)))
                .andExpect(jsonPath("$.purchasesValue").value(0.0))
                .andExpect(jsonPath("$.wasteValue").value(0.0))
                .andExpect(jsonPath("$.countDifferenceValue").value(0.0))
                .andExpect(jsonPath("$.wasteByProduct", hasSize(0)));
    }

    @Test
    void exportsTheStockAsOfNow() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/inventory.csv", venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, equalToIgnoringCase("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"inventario_2030-05-20.csv\""))
                .andReturn());

        assertEquals(List.of(
                "Producto;Categoría;Unidad;Proveedor;Stock actual;Stock mínimo;Bajo mínimo;Coste unitario (EUR);"
                        + "Valor del stock (EUR)",
                // By category and then name; inactive products are left out; typed text can't run as a formula
                "Ginebra;Destilados;Botella;;5,5;;No;;",
                "Ron;Destilados;Botella;'@Bebidas SL;1;2;Sí;10,00;10,00",
                "Barril Mahou;Cerveza;Barril;;2,5;1;No;92,50;231,25",
                "'+Tónica;Refrescos y agua;Unidad;;-5;12;Sí;0,50;0,00"), lines);
    }

    @Test
    void exportsTheMovementsOfTheRangeOldestFirst() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/stock-movements.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"movimientos-stock_2030-05-01_2030-05-15.csv\""))
                .andReturn());

        String by = ";20/05/2030 12:00;First owner Last owner";
        assertEquals(List.of(
                "Fecha;Producto;Unidad;Tipo;Cantidad;Coste unitario (EUR);Valor (EUR);Stock después;Motivo;Pedido;"
                        + "Registrado el;Registrado por",
                // The value has the quantity's sign, unlike the JSON's
                "02/05/2030;Ron;Botella;Compra;10;10,00;100,00;10;;" + by,
                "03/05/2030;Ron;Botella;Merma;-1;10,00;-10,00;9;'=SUMA(A1);" + by,
                "04/05/2030;Ginebra;Botella;Compra;4;;;4;;" + by,
                "05/05/2030;Ron;Botella;Consumo;-8;10,00;-80,00;1;;" + by,
                "06/05/2030;Ginebra;Botella;Merma;-0,5;;;3,5;Botella rota;" + by,
                "07/05/2030;'+Tónica;Unidad;Consumo;-5;0,50;-2,50;-5;;" + by,
                "08/05/2030;Limones;kg;Compra;3;2,00;6,00;3;;" + by,
                "09/05/2030;Limones;kg;Merma;-1;2,00;-2,00;2;Pochos;" + by,
                "10/05/2030;Barril Mahou;Barril;Compra;3;92,50;277,50;3;;" + orderId + by,
                "11/05/2030;Barril Mahou;Barril;Merma;-0,25;92,50;-23,13;2,75;Barril pinchado;" + by,
                "12/05/2030;Barril Mahou;Barril;Recuento;-0,25;92,50;-23,13;2,5;Recuento;" + by), lines);

        // Adding up the column by type gives the report's values of the range
        assertEquals(new BigDecimal("383.50"), valueOf(lines, "Compra"));
        assertEquals(new BigDecimal("-35.13"), valueOf(lines, "Merma"));
        assertEquals(new BigDecimal("-23.13"), valueOf(lines, "Recuento"));
        // The movement listing keeps value as |quantity| x unitCost
        mockMvc.perform(getJson("/api/stock-movements/establishment/{id}?type=COUNT", venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$[0].quantity").value(-0.25))
                .andExpect(jsonPath("$[0].value").value(23.13));
    }

    @Test
    void aCountThatFoundLessIsNegativeInTheExportAndTheReport() throws Exception {
        Long olives = product("Aceitunas", "FOOD", "KG", null, "12", null);
        movement(olives, "PURCHASE", "4", null, "2030-05-13");
        mockMvc.perform(postJson("/api/stock-counts", """
                        { "establishmentId": %d, "date": "2030-05-14",
                          "items": [ { "productId": %d, "countedQuantity": 2.5 } ] }
                        """.formatted(venue.getId(), olives)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].value").value(18.0));

        String range = "?from=2030-05-14&to=2030-05-14";
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/stock-movements.csv" + range,
                venue.getId()).with(as(owner))).andReturn());
        assertEquals("14/05/2030;Aceitunas;kg;Recuento;-1,5;12,00;-18,00;2,5;;;20/05/2030 12:00;First owner Last owner",
                lines.get(1));
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory" + range, venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$.countDifferenceValue").value(-18.0));
    }

    // The "Valor (EUR)" column of the movements of one type added up
    private static BigDecimal valueOf(List<String> lines, String type) {
        return lines.stream().skip(1)
                .map(line -> line.split(";", -1))
                .filter(columns -> columns[3].equals(type) && !columns[6].isEmpty())
                .map(columns -> new BigDecimal(columns[6].replace(',', '.')))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    @Test
    void theRangedReportAndExportNeedABoundedRange() throws Exception {
        for (String path : List.of("inventory", "stock-movements.csv")) {
            String url = "/api/reports/establishment/" + venue.getId() + "/" + path;
            mockMvc.perform(getJson(url + "?from=2030-05-01").with(as(owner)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Missing required parameter 'to'"));
            mockMvc.perform(getJson(url + "?from=2029-01-01&to=2030-01-02").with(as(owner)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));
            mockMvc.perform(getJson(url + "?from=2030-05-02&to=2030-05-01").with(as(owner)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory" + RANGE, 999999).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void runsTheSameQueriesHoweverLongTheRangeAndKeepsTheTenLargestWastes() throws Exception {
        long few = reportStatements(RANGE);

        for (int i = 0; i < 9; i++) {
            Long extra = product("Extra " + i, "OTHER", "UNIT", "1", "1." + i, null);
            movement(extra, "PURCHASE", "5", null, "2030-01-0" + (i + 1));
            movement(extra, "WASTE", "1", "Caducado", "2030-02-0" + (i + 1));
        }
        long many = reportStatements("?from=2029-05-21&to=2030-05-20");

        assertTrue(few > 0, "statistics must be enabled in the test profile");
        assertEquals(few, many);
        // 13 products had waste in the year; the ten largest are kept, the one without a cost last
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory?from=2029-05-21&to=2030-05-20", venue.getId())
                        .with(as(owner)))
                .andExpect(jsonPath("$.wasteByProduct", hasSize(10)))
                .andExpect(jsonPath("$.wasteByProduct[0].productName").value("Barril Mahou"))
                .andExpect(jsonPath("$.wasteByProduct[1].productName").value("Ron"))
                .andExpect(jsonPath("$.wasteByProduct[2].productName").value("Limones"))
                .andExpect(jsonPath("$.wasteByProduct[3].productName").value("Extra 8"))
                .andExpect(jsonPath("$.wasteByProduct[9].productName").value("Extra 2"));
    }

    private long reportStatements(String range) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory" + range, venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** Checks the BOM, the CRLF line ends and the UTF-8 encoding, and returns the lines without the BOM. */
    private static List<String> lines(MvcResult result) {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(body, 3));
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\r\n"));
        return List.of(text.split("\r\n"));
    }

    private Long supplier(String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(venue.getId(), name)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private Long product(String name, String category, String unit, String minStock, String unitCost, Long supplierId)
            throws Exception {
        return longAt(mockMvc.perform(postJson("/api/products", """
                        { "establishmentId": %d, "name": "%s", "category": "%s", "unit": "%s", "minStock": %s,
                          "unitCost": %s, "supplierId": %s }
                        """.formatted(venue.getId(), name, category, unit, minStock, unitCost, supplierId)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void movement(Long productId, String type, String quantity, String reason, String date) throws Exception {
        mockMvc.perform(postJson("/api/stock-movements", """
                        { "productId": %d, "type": "%s", "quantity": %s, "reason": %s, "date": "%s" }
                        """.formatted(productId, type, quantity, reason != null ? "\"" + reason + "\"" : null, date))
                        .with(as(owner)))
                .andExpect(status().isCreated());
    }
}

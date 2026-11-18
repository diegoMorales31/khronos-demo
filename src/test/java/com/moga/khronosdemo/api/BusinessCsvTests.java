package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cash closings, expenses and summary exports open in a Spanish spreadsheet as they are, like the
 * time-tracking ones: BOM, ';', CRLF, Spanish headers and values, decimal commas, and user-typed text
 * that cannot run as a formula. "Now" is Sunday 12 May 2030 at 12:00 in Madrid.
 */
class BusinessCsvTests extends IntegrationTest {

    private static final String RANGE = "?from=2030-05-08&to=2030-05-12";

    private User owner;
    private Establishment venue;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        Worker ana = createWorker(venue, "ana");
        createShift(ana, createZone(venue, "Bar"), LocalDate.of(2030, 5, 11), "10:00", "14:00");
        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));

        mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "2030-05-09", "category": "SUPPLIES",
                          "description": "=HYPERLINK(\\"http://evil.test\\")", "supplier": "+34 600 000 000",
                          "amount": 30, "paymentMethod": "CASH", "notes": "@SUM(A1); more" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated());
        mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "2030-05-10", "category": "PAYROLL",
                          "description": "Nóminas de mayo", "amount": 1500, "paymentMethod": "TRANSFER" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated());
        mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "2030-05-11", "category": "MARKETING",
                          "description": "Flyers", "amount": 60, "paymentMethod": "CARD" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        Long thursday = longAt(mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "2030-05-09", "openingFloat": 100, "cashSales": 400,
                          "cardSales": 600, "countedCash": 460, "notes": "Noche; mucha gente" }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", """
                        { "differenceReason": "-Faltan 10 €" }
                        """, thursday).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "2030-05-10", "openingFloat": 100, "cashSales": 300.50,
                          "cardSales": 450, "otherSales": 49.50, "cashWithdrawals": 50, "countedCash": 335, "tips": 12.5 }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated());
    }

    @Test
    void exportsTheCashClosings() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/cash-closings.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, equalToIgnoringCase("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"cierres-caja_2030-05-08_2030-05-12.csv\""))
                .andReturn());

        assertEquals("Día de servicio;Estado;Fondo inicial (EUR);Ventas en efectivo (EUR);Ventas con tarjeta (EUR);"
                + "Otras ventas (EUR);Ventas totales (EUR);Gastos en efectivo (EUR);Retiradas de efectivo (EUR);"
                + "Efectivo esperado (EUR);Efectivo contado (EUR);Descuadre (EUR);Motivo del descuadre;Propinas (EUR);"
                + "Notas;Cerrado el;Cerrado por", lines.get(0));
        // Earliest day first; closed at 10:00 UTC, 12:00 in Madrid; the reason can't run as a formula
        assertEquals("09/05/2030;Cerrado;100,00;400,00;600,00;0,00;1000,00;30,00;0,00;470,00;460,00;-10,00;"
                + "'-Faltan 10 €;;\"Noche; mucha gente\";12/05/2030 12:00;First owner Last owner", lines.get(1));
        // A draft's figures are worked out as in the listing
        assertEquals("10/05/2030;Borrador;100,00;300,50;450,00;49,50;800,00;0,00;50,00;350,50;335,00;-15,50;;12,50;;;",
                lines.get(2));
        assertEquals(3, lines.size());
    }

    @Test
    void exportsTheExpenses() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/expenses.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"gastos_2030-05-08_2030-05-12.csv\""))
                .andReturn());

        assertEquals("Fecha;Categoría;Descripción;Proveedor;Importe (EUR);Medio de pago;Notas;Registrado por",
                lines.get(0));
        assertEquals("09/05/2030;Género y bebidas;\"'=HYPERLINK(\"\"http://evil.test\"\")\";'+34 600 000 000;30,00;"
                + "Efectivo;\"'@SUM(A1); more\";First owner Last owner", lines.get(1));
        assertEquals("10/05/2030;Nóminas y seguros sociales;Nóminas de mayo;;1500,00;Transferencia;;First owner Last owner",
                lines.get(2));
        assertEquals("11/05/2030;Marketing;Flyers;;60,00;Tarjeta;;First owner Last owner", lines.get(3));
        assertEquals(4, lines.size());
    }

    @Test
    void exportsTheSummaryOneRowPerDay() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/establishment/{id}/summary.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"resumen_2030-05-08_2030-05-12.csv\""))
                .andReturn());

        assertEquals("Fecha;Ventas (EUR);Gastos (EUR);Coste de personal (EUR);Margen (EUR);Estado del cierre;"
                + "Descuadre (EUR)", lines.get(0));
        // Nobody has a rate, so the staff cost is empty and the margin takes off the payroll expense instead
        assertEquals(List.of(
                "08/05/2030;0,00;0,00;;0,00;Sin cierre;",
                "09/05/2030;1000,00;30,00;;970,00;Cerrado;-10,00",
                "10/05/2030;800,00;1500,00;;-700,00;Borrador;-15,50",
                "11/05/2030;0,00;60,00;;-60,00;Falta el cierre;",
                "12/05/2030;0,00;0,00;;0,00;Sin cierre;"), lines.subList(1, lines.size()));
    }

    @Test
    void aDraftWithoutCountedCashLeavesItsCountedCashAndDifferenceEmpty() throws Exception {
        mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "2030-05-12", "openingFloat": 100, "cashSales": 80,
                          "cardSales": 20 }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        List<String> closings = lines(mockMvc.perform(get("/api/reports/establishment/{id}/cash-closings.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("12/05/2030;Borrador;100,00;80,00;20,00;0,00;100,00;0,00;0,00;180,00;;;;;;;", closings.get(3));

        List<String> summary = lines(mockMvc.perform(get("/api/reports/establishment/{id}/summary.csv" + RANGE,
                        venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("12/05/2030;100,00;0,00;;100,00;Borrador;", summary.get(5));
    }

    @Test
    void theExportsNeedABoundedRange() throws Exception {
        for (String file : List.of("cash-closings.csv", "expenses.csv", "summary.csv")) {
            String url = "/api/reports/establishment/" + venue.getId() + "/" + file;
            mockMvc.perform(getJson(url + "?from=2030-05-08").with(as(owner)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Missing required parameter 'to'"));
            mockMvc.perform(getJson(url + "?from=2030-01-01&to=2031-01-02").with(as(owner)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));
            mockMvc.perform(getJson(url + "?from=2030-05-09&to=2030-05-08").with(as(owner)))
                    .andExpect(status().isBadRequest());
        }
    }

    /** Checks the BOM, the CRLF line ends and the UTF-8 encoding, and returns the lines without the BOM. */
    private static List<String> lines(MvcResult result) {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(body, 3));
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\r\n"));
        return List.of(text.split("\r\n"));
    }
}

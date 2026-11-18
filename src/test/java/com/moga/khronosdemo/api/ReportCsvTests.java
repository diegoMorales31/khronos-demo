package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CSV exports open in a Spanish spreadsheet as they are: BOM, ';', CRLF, Spanish headers, local
 * times, and user-typed text that cannot run as a formula.
 */
class ReportCsvTests extends IntegrationTest {

    private static final LocalDate FRI = LocalDate.of(2030, 5, 10);
    private static final String RANGE = "?from=2030-05-10&to=2030-05-12";

    private User owner;
    private Establishment venue;
    private Worker ana;
    private Worker mallory;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        Zone bar = createZone(venue, "Bar");
        ana = createWorker(venue, "ana");
        rename(ana, "Ana María", "García; \"la jefa\"");
        mallory = createWorker(venue, "mallory");
        rename(mallory, "=HYPERLINK(\"http://evil.test\")", "@SUM(A1)");
        workerRateRepository.save(new WorkerRate(ana, new BigDecimal("12.50"), LocalDate.of(2030, 1, 1), Instant.now()));

        WorkerShift night = createShift(ana, bar, FRI, "18:00", "02:00");
        createTimeEntry(ana, night, FRI, "2030-05-10T16:15:00Z", "2030-05-11T00:15:00Z", 30);
        TimeEntry voided = createTimeEntry(ana, null, FRI.plusDays(1), "2030-05-11T08:00:00Z", "2030-05-11T09:00:00Z", 0);
        voided.setVoidedAt(Instant.parse("2030-05-11T10:00:00Z"));
        timeEntryRepository.save(voided);
        createTimeEntry(mallory, null, FRI.plusDays(2), "2030-05-12T07:00:00Z", null, 0);

        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));
    }

    @Test
    void exportsTheHoursReport() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/establishment/{id}/hours.csv" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, equalToIgnoringCase("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"horas_2030-05-10_2030-05-12.csv\""))
                .andReturn();

        List<String> lines = lines(result);
        assertEquals("ID trabajador;Nombre;Apellidos;Activo;Minutos planificados;Minutos trabajados;Diferencia (min);"
                + "Turnos;Fichajes;Retrasos;Turnos sin fichar;Fichajes sin turno;Fichajes abiertos;"
                + "Tarifa actual (EUR/h);Coste planificado (EUR);Coste real (EUR)", lines.get(0));
        // Rows go by first name, and '=' sorts before letters
        assertEquals(mallory.getUserId() + ";\"'=HYPERLINK(\"\"http://evil.test\"\")\";'@SUM(A1);Sí;0;0;0;0;1;0;0;1;1;;;",
                lines.get(1));
        // 480 planned and 450 worked minutes at 12.50 EUR/h; a negative difference stays a plain number
        assertEquals(ana.getUserId() + ";Ana María;\"García; \"\"la jefa\"\"\";Sí;480;450;-30;1;1;1;0;0;0;12,50;100,00;93,75",
                lines.get(2));
        assertEquals(3, lines.size());
    }

    @Test
    void exportsTheTimeTrackingRecordInTheVenuesTimeZone() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/establishment/{id}/time-entries.csv" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-jornada_2030-05-10_2030-05-12.csv\""))
                .andReturn();

        List<String> lines = lines(result);
        assertEquals("Nombre;Apellidos;Día de servicio;Entrada;Salida;Pausa (min);Horas trabajadas;Origen;Corregido;Estado",
                lines.get(0));
        assertEquals("\"'=HYPERLINK(\"\"http://evil.test\"\")\";'@SUM(A1);12/05/2030;12/05/2030 09:00;;0;;"
                + "Trabajador;No;Abierto", lines.get(1));
        // 16:15Z is 18:15 in Madrid; the voided entry is left out
        assertEquals("Ana María;\"García; \"\"la jefa\"\"\";10/05/2030;10/05/2030 18:15;11/05/2030 02:15;30;7:30;"
                + "Trabajador;No;Cerrado", lines.get(2));
        assertEquals(3, lines.size());

        List<String> withVoided = lines(mockMvc.perform(get("/api/reports/establishment/{id}/time-entries.csv" + RANGE
                        + "&includeVoided=true", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn());
        assertEquals("Ana María;\"García; \"\"la jefa\"\"\";11/05/2030;11/05/2030 10:00;11/05/2030 11:00;0;1:00;"
                + "Trabajador;No;Anulado", withVoided.get(3));
    }

    @Test
    void aWorkerDownloadsOnlyTheirOwnRecord() throws Exception {
        List<String> lines = lines(mockMvc.perform(get("/api/reports/worker/{id}/time-entries.csv" + RANGE, ana.getUserId())
                        .with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-jornada_" + ana.getUserId() + "_2030-05-10_2030-05-12.csv\""))
                .andReturn());
        assertEquals(2, lines.size());

        mockMvc.perform(get("/api/reports/worker/{id}/time-entries.csv" + RANGE, mallory.getUserId()).with(as(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/establishment/{id}/time-entries.csv" + RANGE, venue.getId()).with(as(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/establishment/{id}/hours.csv" + RANGE, venue.getId()).with(as(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theExportsNeedABoundedRange() throws Exception {
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours.csv?from=2030-05-10", venue.getId()).with(as(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(getJson("/api/reports/establishment/{id}/time-entries.csv?from=2030-01-01&to=2031-01-02",
                        venue.getId()).with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    /** Checks the BOM, the CRLF line ends and the UTF-8 encoding, and returns the lines without the BOM. */
    private static List<String> lines(MvcResult result) {
        byte[] body = result.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(body, 3));
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertEquals(true, text.endsWith("\r\n"));
        return List.of(text.split("\r\n"));
    }

    private void rename(Worker worker, String firstName, String lastName) {
        User user = userRepository.findById(worker.getUserId()).orElseThrow();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        userRepository.save(user);
    }
}

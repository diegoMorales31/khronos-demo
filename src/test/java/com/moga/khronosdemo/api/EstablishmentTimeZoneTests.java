package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstablishmentTimeZoneTests extends IntegrationTest {

    private User admin;
    private User owner;

    @BeforeEach
    void setUp() {
        admin = createAdmin("admin");
        owner = createOwner("owner");
    }

    @Test
    void anEstablishmentWithoutATimeZoneUsesMadrid() throws Exception {
        mockMvc.perform(postJson("/api/establishments", """
                        { "establishmentName": "Bar Sol", "establishmentAddress": "1 Plaza Mayor", "ownerId": %d }
                        """.formatted(owner.getId())).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("Europe/Madrid"));
    }

    @Test
    void storesTheGivenTimeZoneAndKeepsItWhenAnUpdateLeavesItOut() throws Exception {
        Long id = longAt(mockMvc.perform(postJson("/api/establishments", """
                        { "establishmentName": "Bar Teide", "establishmentAddress": "2 Calle Castillo", "ownerId": %d,
                          "timeZone": "Atlantic/Canary" }
                        """.formatted(owner.getId())).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("Atlantic/Canary"))
                .andReturn(), "$.id");

        mockMvc.perform(putJson("/api/establishments/{id}", """
                        { "establishmentName": "Bar Teide II", "establishmentAddress": "2 Calle Castillo", "ownerId": %d }
                        """.formatted(owner.getId()), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Atlantic/Canary"));

        mockMvc.perform(putJson("/api/establishments/{id}", """
                        { "establishmentName": "Bar Teide II", "establishmentAddress": "2 Calle Castillo", "ownerId": %d,
                          "timeZone": "Europe/Lisbon" }
                        """.formatted(owner.getId()), id).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"));

        assertEquals("Europe/Lisbon", establishmentRepository.findById(id).orElseThrow().getTimeZone());
    }

    @Test
    void rejectsAnUnknownZoneOrAFixedOffset() throws Exception {
        for (String zone : new String[]{"Mars/Olympus_Mons", "+02:00", ""}) {
            mockMvc.perform(postJson("/api/establishments", """
                            { "establishmentName": "Bar", "establishmentAddress": "3 Calle Luna", "ownerId": %d,
                              "timeZone": "%s" }
                            """.formatted(owner.getId(), zone)).with(as(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.timeZone").value("must be an IANA time zone such as Europe/Madrid"));
        }
        assertEquals(0, establishmentRepository.count());
    }

    @Test
    void workersSeeTheirVenuesTimeZone() throws Exception {
        Establishment venue = createEstablishment(owner);
        Worker worker = createWorker(venue, "worker");

        mockMvc.perform(getJson("/api/establishments/{id}", venue.getId()).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Madrid"));
    }
}

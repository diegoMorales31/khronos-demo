package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The difference allowed without a reason comes from app.cash-closing.difference-threshold
@TestPropertySource(properties = "app.cash-closing.difference-threshold=0.50")
class CashClosingThresholdTests extends IntegrationTest {

    @Test
    void theThresholdIsASetting() throws Exception {
        User owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        clock.setNow(Instant.parse("2030-05-10T20:00:00Z"));
        Long withinThreshold = create(owner, venue, "2030-05-09", "100.50");
        Long overThreshold = create(owner, venue, "2030-05-10", "99.40");

        mockMvc.perform(putJson("/api/cash-closings/{id}/close", "{}", withinThreshold).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(putJson("/api/cash-closings/{id}/close", "{}", overThreshold).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The cash difference of -0.60 EUR is larger than the 0.50 EUR"
                        + " allowed without a reason; send a differenceReason to close the day"));
    }

    private Long create(User owner, Establishment venue, String date, String countedCash) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "%s", "openingFloat": 100, "cashSales": 0,
                          "cardSales": 0, "countedCash": %s }
                        """.formatted(venue.getId(), date, countedCash)).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}

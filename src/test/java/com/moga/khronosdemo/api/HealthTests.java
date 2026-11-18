package com.moga.khronosdemo.api;

import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthTests extends IntegrationTest {

    @Test
    void healthIsPublicAndHasNoDetails() throws Exception {
        mockMvc.perform(getJson("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        // The liveness and readiness groups are public too, for container probes
        mockMvc.perform(getJson("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void noOtherActuatorEndpointIsExposed() throws Exception {
        mockMvc.perform(getJson("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(getJson("/actuator/env").with(as(createAdmin("admin")))).andExpect(status().isNotFound());
        mockMvc.perform(getJson("/actuator/info").with(as(createAdmin("admin2")))).andExpect(status().isNotFound());
    }
}

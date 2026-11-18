package com.moga.khronosdemo.api;

import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiDocsTests extends IntegrationTest {

    @Test
    void documentsTheNewEndpointsAndTheDeprecatedToggle() throws Exception {
        mockMvc.perform(getJson("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/workers/with-user'].post").exists())
                .andExpect(jsonPath("$.paths['/api/workers/{userId}/active'].put").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}/password/reset'].put").exists())
                .andExpect(jsonPath("$.paths['/api/users/establishment/{establishmentId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/worker-events/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/worker-shifts/owner/{ownerId}'].get.parameters[*].name", hasItem("to")))
                .andExpect(jsonPath("$.paths['/api/workers/deactivate/{userId}'].delete.deprecated").value(true))
                // The caller is taken from the token, so it must not show up as a parameter
                .andExpect(jsonPath("$.paths['/api/establishments/{id}'].get.parameters[*].name", contains("id")))
                .andExpect(jsonPath("$.paths['/api/workers/with-user'].post.parameters").doesNotExist());
    }

    @Test
    void documentsTimeTrackingRatesAndReports() throws Exception {
        mockMvc.perform(getJson("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/time-entries/clock-in'].post").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/clock-out'].post").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/me/open'].get").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries'].post").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/{id}/void'].put").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/{id}/clock-out'].post").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/{id}/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/time-entries/worker/{workerId}'].get.parameters[*].name",
                        hasItem("includeVoided")))
                .andExpect(jsonPath("$.paths['/api/time-entries/establishment/{establishmentId}/open'].get").exists())
                .andExpect(jsonPath("$.paths['/api/workers/{workerId}/rates'].post").exists())
                .andExpect(jsonPath("$.paths['/api/workers/{workerId}/rates/{rateId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/hours'].get.parameters[*].name",
                        hasItem("from")))
                .andExpect(jsonPath("$.paths['/api/reports/worker/{workerId}/days'].get.parameters[*].name",
                        contains("workerId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/hours.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/time-entries.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/worker/{workerId}/time-entries.csv'].get").exists())
                // The worker clocking in is taken from the token
                .andExpect(jsonPath("$.paths['/api/time-entries/clock-in'].post.parameters").doesNotExist());
    }

    @Test
    void documentsCashClosingsExpensesAndTheSummary() throws Exception {
        mockMvc.perform(getJson("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/cash-closings'].post").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}/close'].put").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}/reopen'].put").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/{id}/history'].get").exists())
                .andExpect(jsonPath("$.paths['/api/cash-closings/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/expenses'].post").exists())
                .andExpect(jsonPath("$.paths['/api/expenses/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/expenses/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/expenses/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "from", "to", "category")))
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/summary'].get.parameters[*].name",
                        contains("establishmentId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/summary.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/cash-closings.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/expenses.csv'].get").exists())
                // The author is taken from the token
                .andExpect(jsonPath("$.paths['/api/cash-closings'].post.parameters").doesNotExist());
    }

    @Test
    void documentsTheInventory() throws Exception {
        mockMvc.perform(getJson("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/suppliers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/suppliers/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/suppliers/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/suppliers/establishment/{establishmentId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/active'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "includeInactive")))
                .andExpect(jsonPath("$.paths['/api/stock-movements'].post").exists())
                .andExpect(jsonPath("$.paths['/api/stock-movements/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "from", "to", "productId", "type")))
                // Movements are never changed or deleted
                .andExpect(jsonPath("$.paths['/api/stock-movements/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/stock-counts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}/order'].put").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}/cancel'].put").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/{id}/receive'].put").exists())
                .andExpect(jsonPath("$.paths['/api/purchase-orders/establishment/{establishmentId}'].get.parameters[*].name",
                        contains("establishmentId", "status", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/inventory'].get.parameters[*].name",
                        contains("establishmentId", "from", "to")))
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/inventory.csv'].get").exists())
                .andExpect(jsonPath("$.paths['/api/reports/establishment/{establishmentId}/stock-movements.csv'].get").exists())
                // The author is taken from the token
                .andExpect(jsonPath("$.paths['/api/stock-movements'].post.parameters").doesNotExist());
    }
}

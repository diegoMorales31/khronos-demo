package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A platform admin reaches every business, so its password needs 16 characters instead of 8. */
class AdminPasswordTests extends IntegrationTest {

    private static final String FIFTEEN = "fifteen-chars-x";
    private static final String SIXTEEN = "sixteen-chars-xy";

    @Test
    void anAdminCannotBeCreatedWithAShortPassword() throws Exception {
        User admin = createAdmin("root");

        mockMvc.perform(postJson("/api/users/create?role=PLATFORM_ADMIN", newUser("second-admin", FIFTEEN)).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("a platform admin's password must be at least 16 characters long"));

        mockMvc.perform(postJson("/api/users/create?role=PLATFORM_ADMIN", newUser("second-admin", SIXTEEN)).with(as(admin)))
                .andExpect(status().isCreated());
    }

    @Test
    void anOwnerStillOnlyNeedsEightCharacters() throws Exception {
        User admin = createAdmin("root");

        mockMvc.perform(postJson("/api/users/create?role=OWNER", newUser("owner", "eight-ch")).with(as(admin)))
                .andExpect(status().isCreated());
    }

    @Test
    void anAdminCannotChangeToAShortPassword() throws Exception {
        User admin = createAdmin("root");

        mockMvc.perform(putJson("/api/users/{id}/password", passwordChange(FIFTEEN), admin.getId()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("a platform admin's password must be at least 16 characters long"));

        mockMvc.perform(putJson("/api/users/{id}/password", passwordChange(SIXTEEN), admin.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void anOwnerCanChangeToAnEightCharacterPassword() throws Exception {
        User owner = createOwner("owner");

        mockMvc.perform(putJson("/api/users/{id}/password", passwordChange("eight-ch"), owner.getId()).with(as(owner)))
                .andExpect(status().isNoContent());
    }

    private static String newUser(String username, String password) {
        return """
                { "username": "%s", "password": "%s", "firstName": "First", "lastName": "Last",
                  "email": "%s@example.test" }
                """.formatted(username, password, username);
    }

    private static String passwordChange(String newPassword) {
        return """
                { "currentPassword": "%s", "newPassword": "%s" }
                """.formatted(PASSWORD, newPassword);
    }
}

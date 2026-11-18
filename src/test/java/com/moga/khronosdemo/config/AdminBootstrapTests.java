package com.moga.khronosdemo.config;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBootstrapTests extends IntegrationTest {

    private AdminBootstrap bootstrap(String username, String password, String email) {
        return new AdminBootstrap(new AdminProperties(username, password, email), userRepository, passwordEncoder);
    }

    @Test
    void createsThePlatformAdminWhoCanThenSignIn() throws Exception {
        bootstrap("root", "first-admin-pass", "root@example.test").createAdminIfMissing();

        User admin = userRepository.findByUsername("root").orElseThrow();
        assertEquals(Role.PLATFORM_ADMIN, admin.getRole());
        assertEquals("root@example.test", admin.getEmail());
        assertNotEquals("first-admin-pass", admin.getPassword());
        assertTrue(passwordEncoder.matches("first-admin-pass", admin.getPassword()));

        mockMvc.perform(postJson("/api/auth/login", """
                        { "username": "root", "password": "first-admin-pass" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void runningAgainNeverChangesTheExistingAccount() {
        bootstrap("root", "first-admin-pass", "root@example.test").createAdminIfMissing();
        String storedPassword = userRepository.findByUsername("root").orElseThrow().getPassword();

        bootstrap("root", "another-password", "other@example.test").createAdminIfMissing();

        assertEquals(1, userRepository.count());
        User admin = userRepository.findByUsername("root").orElseThrow();
        assertEquals(storedPassword, admin.getPassword());
        assertEquals("root@example.test", admin.getEmail());
    }

    @Test
    void leavesAnExistingUserWithThatUsernameAlone() {
        User owner = createOwner("root");

        bootstrap("root", "first-admin-pass", "root-admin@example.test").createAdminIfMissing();

        User unchanged = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals(Role.OWNER, unchanged.getRole());
        assertEquals(owner.getPassword(), unchanged.getPassword());
        assertEquals(1, userRepository.count());
    }

    @Test
    void doesNothingWhenTheSettingsAreMissing() {
        bootstrap("", "", "").createAdminIfMissing();
        bootstrap("root", null, "root@example.test").createAdminIfMissing();

        assertEquals(0, userRepository.count());
    }

    @Test
    void refusesAPasswordTheApiWouldReject() {
        assertThrows(IllegalStateException.class,
                () -> bootstrap("root", "short", "root@example.test").createAdminIfMissing());
        assertThrows(IllegalStateException.class,
                () -> bootstrap("root", "x".repeat(73), "root@example.test").createAdminIfMissing());

        assertEquals(0, userRepository.count());
    }
}

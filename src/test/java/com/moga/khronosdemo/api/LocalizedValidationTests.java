package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The field messages of a failed validation come in the language the request asks for (Accept-Language),
 * Spanish or English, and English otherwise. The main message stays in English, which clients read.
 */
class LocalizedValidationTests extends IntegrationTest {

    private User owner;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
    }

    private static final String BAD_PROFILE = """
            { "username": "owner", "firstName": "", "lastName": "Gil", "email": "not-an-email", "phoneNumber": "12" }
            """;

    @Test
    void fieldMessagesFollowTheRequestedLanguage() throws Exception {
        mockMvc.perform(putJson("/api/users/{id}", BAD_PROFILE, owner.getId()).with(as(owner))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.firstName").value("es obligatorio"))
                .andExpect(jsonPath("$.fieldErrors.email").value("debe ser un correo electrónico válido"))
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").value("debe ser un número de teléfono válido"));
    }

    @Test
    void englishWhenAskedForIt_orForAnyOtherLanguage_orWithoutTheHeader() throws Exception {
        for (String language : new String[]{"en", "fr-FR", null}) {
            var request = putJson("/api/users/{id}", BAD_PROFILE, owner.getId()).with(as(owner));
            if (language != null) {
                request.header(HttpHeaders.ACCEPT_LANGUAGE, language);
            }
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstName").value("must not be blank"))
                    .andExpect(jsonPath("$.fieldErrors.phoneNumber").value("must be a valid phone number"));
        }
    }

    @Test
    void theAppsOwnConstraintsHaveTheirLimitInTheMessage() throws Exception {
        mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                        { "newPassword": "%s" }
                        """.formatted("ñ".repeat(40)), owner.getId()).with(as(createAdmin("admin")))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("es demasiado larga: como mucho 72 bytes"));
    }
}

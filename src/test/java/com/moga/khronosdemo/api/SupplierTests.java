package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An establishment's suppliers: create, list by name, edit and delete, and names that are unique within
 * the establishment whatever their case, in the service and in the database.
 */
class SupplierTests extends IntegrationTest {

    private User owner;
    private Establishment venue;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
    }

    @Test
    void createsListsEditsAndDeletesSuppliers() throws Exception {
        Long mahou = longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "  Mahou ", "phone": " 910 000 000 ", "email": "pedidos@mahou.test",
                          "notes": "  " }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.name").value("Mahou"))
                .andExpect(jsonPath("$.phone").value("910 000 000"))
                .andExpect(jsonPath("$.email").value("pedidos@mahou.test"))
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andReturn(), "$.id");
        supplier(venue, "cash & carry");
        supplier(venue, "Bodegas Ruiz");

        // By name, whatever its case
        mockMvc.perform(getJson("/api/suppliers/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Bodegas Ruiz", "cash & carry", "Mahou")));

        mockMvc.perform(putJson("/api/suppliers/{id}", """
                        { "name": "Mahou San Miguel", "phone": null, "email": null, "notes": "Viene los martes" }
                        """, mahou).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mahou San Miguel"))
                .andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.notes").value("Viene los martes"));

        mockMvc.perform(deleteJson("/api/suppliers/{id}", mahou).with(as(owner)))
                .andExpect(status().isNoContent());
        assertFalse(supplierRepository.existsById(mahou));
        mockMvc.perform(deleteJson("/api/suppliers/{id}", mahou).with(as(createAdmin("admin"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void namesAreUniqueWithinAnEstablishmentWhateverTheirCase() throws Exception {
        Long mahou = supplier(venue, "Mahou");
        Long ruiz = supplier(venue, "Bodegas Ruiz");

        mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": " MAHOU " }
                        """.formatted(venue.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A supplier named 'MAHOU' already exists at this establishment"))
                .andExpect(jsonPath("$.code").value("SUPPLIER_NAME_TAKEN"));
        mockMvc.perform(putJson("/api/suppliers/{id}", """
                        { "name": "mahou" }
                        """, ruiz).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A supplier named 'mahou' already exists at this establishment"))
                .andExpect(jsonPath("$.code").value("SUPPLIER_NAME_TAKEN"));

        // A supplier can change the case of its own name, and another venue can use the same name
        mockMvc.perform(putJson("/api/suppliers/{id}", """
                        { "name": "MAHOU" }
                        """, mahou).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MAHOU"));
        supplier(createEstablishment(owner), "Mahou");
        assertEquals(3, supplierRepository.count());
    }

    @Test
    void theDatabaseKeepsNamesUniqueToo() throws Exception {
        supplier(venue, "Mahou");
        DataIntegrityViolationException duplicate = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("insert into suppliers (id, establishment_id, name) values (-1, ?, 'mAhOu')",
                        venue.getId()));
        assertTrue(duplicate.getMostSpecificCause().getMessage().contains("ux_suppliers_establishment_name"));
    }

    @Test
    void validatesTheRequest() throws Exception {
        mockMvc.perform(postJson("/api/suppliers", """
                        { "name": " ", "phone": "%s", "email": "not-an-email", "notes": "%s" }
                        """.formatted("1".repeat(31), "n".repeat(501))).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.establishmentId").value("must not be null"))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("size must be between 0 and 30"))
                .andExpect(jsonPath("$.fieldErrors.email").value("must be a well-formed email address"))
                .andExpect(jsonPath("$.fieldErrors.notes").value("size must be between 0 and 500"));
        mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(venue.getId(), "s".repeat(101))).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("size must be between 0 and 100"));
        assertEquals(0, supplierRepository.count());
    }

    @Test
    void anEditKeepsTheSupplierInItsEstablishment() throws Exception {
        Long mahou = supplier(venue, "Mahou");
        Establishment other = createEstablishment(owner);
        mockMvc.perform(putJson("/api/suppliers/{id}", """
                        { "establishmentId": %d, "name": "Mahou" }
                        """.formatted(other.getId()), mahou).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()));
    }

    private Long supplier(Establishment establishment, String name) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/suppliers", """
                        { "establishmentId": %d, "name": "%s" }
                        """.formatted(establishment.getId(), name)).with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}

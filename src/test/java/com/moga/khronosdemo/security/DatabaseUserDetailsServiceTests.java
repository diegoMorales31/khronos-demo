package com.moga.khronosdemo.security;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account is read again on every request with a token, so each lookup is one query, and it still
 * tells an active worker from a deactivated one.
 */
class DatabaseUserDetailsServiceTests extends IntegrationTest {

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Worker active;
    private Worker inactive;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        active = createWorker(venue, "active");
        inactive = createWorker(venue, "inactive");
        inactive.setActive(false);
        workerRepository.save(inactive);
    }

    @Test
    void loadsAnAccountByIdWithOneQuery() {
        SecurityUser ownerAccount = loadById(owner.getId());
        assertTrue(ownerAccount.isEnabled());
        assertEquals(Role.OWNER, ownerAccount.getRole());
        assertEquals("owner", ownerAccount.getUsername());

        SecurityUser activeWorker = loadById(active.getUserId());
        assertTrue(activeWorker.isEnabled());
        assertEquals(Role.WORKER, activeWorker.getRole());

        assertFalse(loadById(inactive.getUserId()).isEnabled());
        assertTrue(userDetailsService.loadUserById(999_999L).isEmpty());
    }

    @Test
    void loadsAnAccountByUsernameWithOneQuery() {
        Statistics statistics = statistics();
        statistics.clear();
        UserDetails found = userDetailsService.loadUserByUsername("inactive");
        assertEquals(1, statistics.getPrepareStatementCount());

        assertFalse(found.isEnabled());
        assertTrue(passwordEncoder.matches(PASSWORD, found.getPassword()));
        assertTrue(userDetailsService.loadUserByUsername("owner").isEnabled());
        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("nobody"));
    }

    private SecurityUser loadById(Long id) {
        Statistics statistics = statistics();
        statistics.clear();
        SecurityUser user = userDetailsService.loadUserById(id).orElseThrow();
        assertEquals(1, statistics.getPrepareStatementCount(), "statements to load user " + id);
        return user;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}

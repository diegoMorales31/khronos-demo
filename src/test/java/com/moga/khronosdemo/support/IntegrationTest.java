package com.moga.khronosdemo.support;

import com.jayway.jsonpath.JsonPath;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.entities.enums.TimeEntrySource;
import com.moga.khronosdemo.repositories.CashClosingChangeRepository;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.EventRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.ProductRepository;
import com.moga.khronosdemo.repositories.StockMovementRepository;
import com.moga.khronosdemo.repositories.SupplierRepository;
import com.moga.khronosdemo.repositories.TimeEntryChangeRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerEventRepository;
import com.moga.khronosdemo.repositories.WorkerRateRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.repositories.ZoneRepository;
import com.moga.khronosdemo.security.DatabaseUserDetailsService;
import com.moga.khronosdemo.security.TokenService;
import com.moga.khronosdemo.services.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base class for tests that run the whole application against the PostgreSQL database given by
 * DB_URL, DB_USERNAME and DB_PASSWORD. Flyway builds the schema, and every table is emptied
 * before and after each test, so tests never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-42";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static String encodedPassword;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EstablishmentRepository establishmentRepository;

    @Autowired
    protected ZoneRepository zoneRepository;

    @Autowired
    protected WorkerRepository workerRepository;

    @Autowired
    protected WorkerShiftRepository workerShiftRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected WorkerEventRepository workerEventRepository;

    @Autowired
    protected WorkerRateRepository workerRateRepository;

    @Autowired
    protected TimeEntryRepository timeEntryRepository;

    @Autowired
    protected TimeEntryChangeRepository timeEntryChangeRepository;

    @Autowired
    protected ExpenseRepository expenseRepository;

    @Autowired
    protected CashClosingRepository cashClosingRepository;

    @Autowired
    protected CashClosingChangeRepository cashClosingChangeRepository;

    @Autowired
    protected SupplierRepository supplierRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected StockMovementRepository stockMovementRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TestClock clock;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    // Notifications are emailed after commit on another thread. The mock keeps the tests offline
    // and stops those threads from writing to email_logs while the next test cleans the database.
    @MockitoBean
    protected EmailService emailService;

    @BeforeEach
    @AfterEach
    void useRealTime() {
        clock.useRealTime();
    }

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        List<String> tables = jdbcTemplate.queryForList("""
                select tablename from pg_tables
                where schemaname = current_schema() and tablename <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("truncate table " + String.join(", ", tables) + " cascade");
        }
    }

    // ---- Fixtures, saved straight through the repositories ----

    protected User createUser(String username, Role role) {
        User user = new User(username, encodedPassword(), "First " + username, "Last " + username,
                null, username + "@example.test");
        user.setRole(role);
        return userRepository.save(user);
    }

    protected User createOwner(String username) {
        return createUser(username, Role.OWNER);
    }

    protected User createAdmin(String username) {
        return createUser(username, Role.PLATFORM_ADMIN);
    }

    protected Establishment createEstablishment(User owner) {
        int n = SEQUENCE.incrementAndGet();
        return establishmentRepository.save(new Establishment("Venue " + n, n + " Test Street", owner));
    }

    protected Zone createZone(Establishment establishment, String name) {
        return zoneRepository.save(new Zone(name, establishment));
    }

    protected Worker createWorker(Establishment establishment, String username) {
        return workerRepository.save(new Worker(createUser(username, Role.WORKER), establishment));
    }

    protected WorkerShift createShift(Worker worker, Zone zone, LocalDate date, String start, String end) {
        return workerShiftRepository.save(new WorkerShift(worker, zone, date, LocalTime.parse(start), LocalTime.parse(end)));
    }

    protected Event createEvent(Zone zone, LocalDateTime dateTime) {
        return eventRepository.save(new Event("Event " + SEQUENCE.incrementAndGet(), dateTime, 50, zone, null));
    }

    protected WorkerEvent assign(Worker worker, Event event) {
        return workerEventRepository.save(new WorkerEvent(worker, event));
    }

    /** A time entry saved as is, without the API's checks or history. clockOut null leaves it open. */
    protected TimeEntry createTimeEntry(Worker worker, WorkerShift shift, LocalDate businessDate,
                                       String clockIn, String clockOut, int breakMinutes) {
        TimeEntry entry = new TimeEntry(worker, Instant.parse(clockIn), TimeEntrySource.WORKER, null, Instant.parse(clockIn));
        entry.setShift(shift);
        entry.setBusinessDate(businessDate);
        entry.setClockOut(clockOut != null ? Instant.parse(clockOut) : null);
        entry.setBreakMinutes(breakMinutes);
        return timeEntryRepository.save(entry);
    }

    private String encodedPassword() {
        if (encodedPassword == null) {
            encodedPassword = passwordEncoder.encode(PASSWORD);
        }
        return encodedPassword;
    }

    // ---- Requests ----

    /** Signs the request with a fresh token for the user, without going through the login endpoint. */
    protected RequestPostProcessor as(User user) {
        return as(user.getId());
    }

    protected RequestPostProcessor as(Worker worker) {
        return as(worker.getUserId());
    }

    protected RequestPostProcessor as(Long userId) {
        return bearer(tokenFor(userId));
    }

    protected String tokenFor(Long userId) {
        return tokenService.issue(userDetailsService.loadUserById(userId).orElseThrow()).accessToken();
    }

    protected RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    protected MockHttpServletRequestBuilder getJson(String url, Object... vars) {
        return get(url, vars).accept(MediaType.APPLICATION_JSON);
    }

    protected MockHttpServletRequestBuilder postJson(String url, String body, Object... vars) {
        return post(url, vars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder putJson(String url, String body, Object... vars) {
        return put(url, vars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder deleteJson(String url, Object... vars) {
        return delete(url, vars);
    }

    protected static Long longAt(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    protected static String uniqueName(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }
}

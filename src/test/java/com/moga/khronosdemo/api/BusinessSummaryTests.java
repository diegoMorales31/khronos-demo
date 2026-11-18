package com.moga.khronosdemo.api;

import com.jayway.jsonpath.JsonPath;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The business summary over a controlled set of closings, expenses, shifts and entries. The venue is in
 * Madrid (UTC+2 in May) and "now" is Sunday 12 May 2030 at 12:00 local time. The range is Wednesday 8 to
 * Monday 13 May; the previous period is Thursday 2 to Tuesday 7 May.
 *
 * <pre>
 * Ana (10 €/h, 12 €/h from Sat 11)   Ben (no rate)
 *
 * Wed 8   Ana's only entry is voided                                              NONE
 * Thu 9   closed: 400 cash + 600 card = 1000, counted 468 for 470 expected (-2)    CLOSED
 *         expenses: 30 cash SUPPLIES, 900 RENT; Ana 18:00-23:00 worked (300 min = 50.00)
 * Fri 10  draft: 300.50 cash + 450 card + 49.50 other = 800, counted 340 for 335 (+5)  DRAFT
 *         expenses: 15.50 cash MAINTENANCE, 1500 PAYROLL; Ana 10:00-12:00 without a shift
 *         (120 min = 20.00); Ben 18:00-02:00 worked, no rate
 * Sat 11  no closing; 60 MARKETING by card; Ana's 10:00-14:00 shift, missed          MISSING
 * Sun 12  today: Ben is clocked in, no closing yet                                   NONE
 * Mon 13  Ana's 20:00-23:00 shift is planned                                         NONE
 * Previous period: Mon 6 closed with 500 cash sales; Ana worked 180 min (30.00); Tue 7 100 SUPPLIES by card
 * </pre>
 */
class BusinessSummaryTests extends IntegrationTest {

    private static final String RANGE = "?from=2030-05-08&to=2030-05-13";
    private static final LocalDate WED = LocalDate.of(2030, 5, 8);
    private static final LocalDate THU = WED.plusDays(1);
    private static final LocalDate FRI = WED.plusDays(2);
    private static final LocalDate SAT = WED.plusDays(3);
    private static final LocalDate SUN = WED.plusDays(4);
    private static final LocalDate MON = WED.plusDays(5);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Establishment venue;
    private Zone bar;
    private Worker ana;
    private Worker ben;

    @BeforeEach
    void setUp() throws Exception {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        bar = createZone(venue, "Bar");
        ana = createWorker(venue, "ana");
        ben = createWorker(venue, "ben");
        rate(ana, "10.00", LocalDate.of(2030, 1, 1));
        rate(ana, "12.00", SAT);
        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));

        TimeEntry voided = createTimeEntry(ana, null, WED, "2030-05-08T08:00:00Z", "2030-05-08T12:00:00Z", 0);
        voided.setVoidedAt(Instant.parse("2030-05-08T13:00:00Z"));
        timeEntryRepository.save(voided);

        createTimeEntry(ana, createShift(ana, bar, THU, "18:00", "23:00"), THU, "2030-05-09T16:00:00Z", "2030-05-09T21:00:00Z", 0);
        expense(venue, THU, "SUPPLIES", "CASH", "30");
        expense(venue, THU, "RENT", "TRANSFER", "900");
        Long thursday = closing(venue, THU, "100", "400", "600", "0", "0", "468", "20");
        mockMvc.perform(put("/api/cash-closings/{id}/close", thursday).with(as(owner))).andExpect(status().isOk());

        createTimeEntry(ana, null, FRI, "2030-05-10T08:00:00Z", "2030-05-10T10:00:00Z", 0);
        createTimeEntry(ben, createShift(ben, bar, FRI, "18:00", "02:00"), FRI, "2030-05-10T16:00:00Z", "2030-05-11T00:00:00Z", 0);
        expense(venue, FRI, "MAINTENANCE", "CASH", "15.50");
        expense(venue, FRI, "PAYROLL", "TRANSFER", "1500");
        closing(venue, FRI, "100", "300.50", "450", "49.50", "50", "340", null);

        createShift(ana, bar, SAT, "10:00", "14:00");
        expense(venue, SAT, "MARKETING", "CARD", "60");

        createTimeEntry(ben, null, SUN, "2030-05-12T08:00:00Z", null, 0);
        createShift(ana, bar, MON, "20:00", "23:00");

        // The previous period, and an expense before it that counts nowhere
        LocalDate previousMonday = LocalDate.of(2030, 5, 6);
        createTimeEntry(ana, null, previousMonday, "2030-05-06T16:00:00Z", "2030-05-06T19:00:00Z", 0);
        Long monday = closing(venue, previousMonday, "0", "500", "0", "0", "0", "500", null);
        mockMvc.perform(put("/api/cash-closings/{id}/close", monday).with(as(owner))).andExpect(status().isOk());
        expense(venue, LocalDate.of(2030, 5, 7), "SUPPLIES", "CARD", "100");
        expense(venue, LocalDate.of(2030, 5, 1), "TAXES", "TRANSFER", "333");

        // Another owner's venue must not leak into the summary
        User otherOwner = createOwner("other-owner");
        Establishment otherVenue = createEstablishment(otherOwner);
        expense(otherVenue, THU, "RENT", "TRANSFER", "5000");
        closing(otherVenue, THU, "0", "9999", "0", "0", "0", "9999", null);
    }

    @Test
    void addsUpSalesExpensesStaffCostAndMargin() throws Exception {
        MvcResult result = mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2030-05-08"))
                .andExpect(jsonPath("$.to").value("2030-05-13"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.totals.sales").value(1800.0))
                .andExpect(jsonPath("$.totals.cashSales").value(700.5))
                .andExpect(jsonPath("$.totals.cardSales").value(1050.0))
                .andExpect(jsonPath("$.totals.otherSales").value(49.5))
                .andExpect(jsonPath("$.totals.tips").value(20.0))
                .andExpect(jsonPath("$.totals.expenses").value(2505.5))
                .andExpect(jsonPath("$.totals.payrollExpenses").value(1500.0))
                // Ana's 300 + 120 minutes at 10 €/h; Ben has no rate, so his Friday is left out and the cost is partial
                .andExpect(jsonPath("$.totals.laborCost").value(70.0))
                .andExpect(jsonPath("$.totals.laborCostPartial").value(true))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(1))
                // Ana's shifts: 300 minutes at 10 €/h, 240 + 180 at 12 €/h
                .andExpect(jsonPath("$.totals.plannedLaborCost").value(134.0))
                // 1800 - (2505.50 - 1500) - 70: the cost of the hours replaces the payroll expenses
                .andExpect(jsonPath("$.totals.grossMargin").value(724.5))
                .andExpect(jsonPath("$.totals.laborCostSource").value("HOURS"))
                // 70 / 1800 = 3.89 %
                .andExpect(jsonPath("$.totals.laborCostPercent").value(3.9))
                // Only Thursday is closed; Friday's +5 is a draft's and may still change
                .andExpect(jsonPath("$.totals.cashDifference").value(-2.0))
                .andExpect(jsonPath("$.totals.draftCashDifference").value(5.0))
                .andExpect(jsonPath("$.totals.closedDays").value(1))
                .andExpect(jsonPath("$.totals.draftDays").value(1))
                .andExpect(jsonPath("$.totals.missingClosingDays").value(1))

                .andExpect(jsonPath("$.days", hasSize(6)))
                .andExpect(jsonPath("$.days[*].date", contains("2030-05-08", "2030-05-09", "2030-05-10", "2030-05-11",
                        "2030-05-12", "2030-05-13")))
                .andExpect(jsonPath("$.days[*].sales", contains(0.0, 1000.0, 800.0, 0.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.days[*].expenses", contains(0.0, 930.0, 1515.5, 60.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.days[*].laborCost", contains(0.0, 50.0, 20.0, 0.0, 0.0, 0.0)))
                // Friday leaves the 1500 of payroll out: 800 - 15.50 - 20
                .andExpect(jsonPath("$.days[*].margin", contains(0.0, 20.0, 764.5, -60.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.days[*].closingStatus", contains("NONE", "CLOSED", "DRAFT", "MISSING", "NONE", "NONE")))
                .andExpect(jsonPath("$.days[*].cashDifference", contains(null, -2.0, 5.0, null, null, null)))

                .andExpect(jsonPath("$.expensesByCategory[*].category",
                        contains("PAYROLL", "RENT", "MARKETING", "SUPPLIES", "MAINTENANCE")))
                .andExpect(jsonPath("$.expensesByCategory[*].amount", contains(1500.0, 900.0, 60.0, 30.0, 15.5)))

                .andExpect(jsonPath("$.previous.from").value("2030-05-02"))
                .andExpect(jsonPath("$.previous.to").value("2030-05-07"))
                .andExpect(jsonPath("$.previous.sales").value(500.0))
                .andExpect(jsonPath("$.previous.expenses").value(100.0))
                .andExpect(jsonPath("$.previous.laborCost").value(30.0))
                .andExpect(jsonPath("$.previous.grossMargin").value(370.0))
                .andExpect(jsonPath("$.previous.laborCostSource").value("HOURS"))
                .andReturn();

        // Money always goes out with two decimals, the percentage with one
        String json = result.getResponse().getContentAsString();
        assertTrue(json.contains("\"sales\":1800.00"), json);
        assertTrue(json.contains("\"laborCostPercent\":3.9,"), json);
        assertTrue(json.contains("\"margin\":-60.00"), json);
    }

    @Test
    void theStaffCostIsTheHoursReportsActualCost() throws Exception {
        MvcResult hours = mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult summary = mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andReturn();
        Object actualCost = JsonPath.read(hours.getResponse().getContentAsString(), "$.totals.actualCost");
        Object plannedCost = JsonPath.read(hours.getResponse().getContentAsString(), "$.totals.plannedCost");
        Object costPartial = JsonPath.read(hours.getResponse().getContentAsString(), "$.totals.costPartial");
        Object workersWithoutCost = JsonPath.read(hours.getResponse().getContentAsString(), "$.totals.workersWithoutCost");
        assertEquals(actualCost, JsonPath.read(summary.getResponse().getContentAsString(), "$.totals.laborCost"));
        assertEquals(plannedCost, JsonPath.read(summary.getResponse().getContentAsString(), "$.totals.plannedLaborCost"));
        assertEquals(costPartial, JsonPath.read(summary.getResponse().getContentAsString(), "$.totals.laborCostPartial"));
        assertEquals(workersWithoutCost,
                JsonPath.read(summary.getResponse().getContentAsString(), "$.totals.workersWithoutCost"));
    }

    @Test
    void withoutRatesTheMarginTakesOffThePayrollExpensesInstead() throws Exception {
        User owner2 = createOwner("owner2");
        Establishment noRates = createEstablishment(owner2);
        Worker carla = createWorker(noRates, "carla");
        createTimeEntry(carla, null, THU, "2030-05-09T08:00:00Z", "2030-05-09T16:00:00Z", 0);
        closing(noRates, THU, "0", "250", "0", "0", "0", "250", null);
        expense(noRates, THU, "SUPPLIES", "CARD", "40");
        expense(noRates, THU, "PAYROLL", "TRANSFER", "1000");
        expense(noRates, LocalDate.of(2030, 5, 6), "PAYROLL", "TRANSFER", "300");

        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, noRates.getId()).with(as(owner2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.laborCost").value(nullValue()))
                // An unknown cost is not a partial one, but Carla is still counted as worked without a cost
                .andExpect(jsonPath("$.totals.laborCostPartial").value(false))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(1))
                .andExpect(jsonPath("$.totals.plannedLaborCost").value(nullValue()))
                .andExpect(jsonPath("$.totals.laborCostPercent").value(nullValue()))
                // 250 - 40 - 1000: with no cost from the hours, the payroll expenses are the staff cost
                .andExpect(jsonPath("$.totals.grossMargin").value(-790.0))
                .andExpect(jsonPath("$.totals.laborCostSource").value("PAYROLL_EXPENSES"))
                .andExpect(jsonPath("$.days[1].laborCost").value(nullValue()))
                .andExpect(jsonPath("$.days[1].margin").value(-790.0))
                .andExpect(jsonPath("$.days[0].laborCost").value(nullValue()))
                .andExpect(jsonPath("$.days[0].margin").value(0.0))
                // The previous period takes off its own payroll expenses
                .andExpect(jsonPath("$.previous.laborCost").value(nullValue()))
                .andExpect(jsonPath("$.previous.expenses").value(300.0))
                .andExpect(jsonPath("$.previous.grossMargin").value(-300.0))
                .andExpect(jsonPath("$.previous.laborCostSource").value("PAYROLL_EXPENSES"));
    }

    @Test
    void aDraftWithoutCountedCashCountsItsSalesButNoDifference() throws Exception {
        User owner2 = createOwner("owner2");
        Establishment uncounted = createEstablishment(owner2);
        closing(uncounted, THU, "100", "200", "0", "0", "0", "290", null);
        closing(uncounted, FRI, "100", "300", "50", "0", "0", null, null);

        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, uncounted.getId()).with(as(owner2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sales").value(550.0))
                .andExpect(jsonPath("$.totals.draftDays").value(2))
                .andExpect(jsonPath("$.totals.cashDifference").value(0.0))
                // Thursday's -10; Friday's cash hasn't been counted, so it has no difference yet
                .andExpect(jsonPath("$.totals.draftCashDifference").value(-10.0))
                .andExpect(jsonPath("$.days[*].closingStatus", contains("NONE", "DRAFT", "DRAFT", "NONE", "NONE", "NONE")))
                .andExpect(jsonPath("$.days[*].sales", contains(0.0, 200.0, 350.0, 0.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.days[*].cashDifference", contains(null, -10.0, null, null, null, null)));
    }

    @Test
    void withoutSalesThePercentageIsUnknownAndTheMarginIsALoss() throws Exception {
        User owner2 = createOwner("owner2");
        Establishment noSales = createEstablishment(owner2);
        Worker dani = createWorker(noSales, "dani");
        rate(dani, "9.00", LocalDate.of(2030, 1, 1));
        createTimeEntry(dani, null, FRI, "2030-05-10T08:00:00Z", "2030-05-10T12:00:00Z", 0);

        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + RANGE, noSales.getId()).with(as(owner2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.sales").value(0.0))
                .andExpect(jsonPath("$.totals.laborCost").value(36.0))
                .andExpect(jsonPath("$.totals.laborCostPartial").value(false))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(0))
                .andExpect(jsonPath("$.totals.laborCostPercent").value(nullValue()))
                .andExpect(jsonPath("$.totals.grossMargin").value(-36.0))
                .andExpect(jsonPath("$.totals.cashDifference").value(0.0))
                .andExpect(jsonPath("$.totals.draftCashDifference").value(0.0))
                .andExpect(jsonPath("$.totals.missingClosingDays").value(1))
                .andExpect(jsonPath("$.days[*].closingStatus", contains("NONE", "NONE", "MISSING", "NONE", "NONE", "NONE")))
                .andExpect(jsonPath("$.days[*].laborCost", contains(0.0, 0.0, 36.0, 0.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.expensesByCategory", hasSize(0)))
                .andExpect(jsonPath("$.previous.sales").value(0.0))
                .andExpect(jsonPath("$.previous.laborCost").value(0.0));
    }

    @Test
    void requiresABoundedRange() throws Exception {
        String summary = "/api/reports/establishment/" + venue.getId() + "/summary";
        mockMvc.perform(getJson(summary + "?from=2030-05-08").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'to'"));
        mockMvc.perform(getJson(summary + "?from=2030-05-09&to=2030-05-08").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
        mockMvc.perform(getJson(summary + "?from=2030-01-01&to=2031-01-02").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));
        mockMvc.perform(getJson(summary + "?from=2030-01-01&to=2031-01-01").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(366)))
                .andExpect(jsonPath("$.previous.from").value("2028-12-31"))
                .andExpect(jsonPath("$.previous.to").value("2029-12-31"));
        // A single day compares with the day before
        mockMvc.perform(getJson(summary + "?from=2030-05-09&to=2030-05-09").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(1)))
                .andExpect(jsonPath("$.previous.from").value("2030-05-08"))
                .andExpect(jsonPath("$.previous.to").value("2030-05-08"));
    }

    @Test
    void runsTheSameNumberOfQueriesHoweverLongTheRangeAndHoweverMuchData() throws Exception {
        long week = statements("?from=2030-05-08&to=2030-05-13");

        for (int day = 0; day < 4; day++) {
            LocalDate date = LocalDate.of(2030, 4, 20).plusDays(day);
            Worker extra = createWorker(venue, uniqueName("extra"));
            rate(extra, "11.00", LocalDate.of(2030, 1, 1));
            createTimeEntry(extra, createShift(extra, bar, date, "10:00", "14:00"), date, date + "T08:00:00Z",
                    date + "T12:00:00Z", 0);
            expense(venue, date, "OTHER", "CASH", "1");
            closing(venue, date, "0", "10", "0", "0", "0", "9", null);
        }
        long year = statements("?from=2029-05-13&to=2030-05-13");

        assertTrue(week > 0, "statistics must be enabled in the test profile");
        assertEquals(week, year);
    }

    private long statements(String range) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(getJson("/api/reports/establishment/{id}/summary" + range, venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void expense(Establishment establishment, LocalDate date, String category, String paymentMethod,
                         String amount) throws Exception {
        mockMvc.perform(postJson("/api/expenses", """
                        { "establishmentId": %d, "date": "%s", "category": "%s", "description": "Expense",
                          "amount": %s, "paymentMethod": "%s" }
                        """.formatted(establishment.getId(), date, category, amount, paymentMethod))
                        .with(as(establishment.getOwner())))
                .andExpect(status().isCreated());
    }

    private Long closing(Establishment establishment, LocalDate date, String openingFloat, String cashSales,
                         String cardSales, String otherSales, String cashWithdrawals, String countedCash,
                         String tips) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/cash-closings", """
                        { "establishmentId": %d, "businessDate": "%s", "openingFloat": %s, "cashSales": %s,
                          "cardSales": %s, "otherSales": %s, "cashWithdrawals": %s, "countedCash": %s, "tips": %s }
                        """.formatted(establishment.getId(), date, openingFloat, cashSales, cardSales, otherSales,
                        cashWithdrawals, countedCash, tips)).with(as(establishment.getOwner())))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    private void rate(Worker worker, String hourlyRate, LocalDate effectiveFrom) {
        workerRateRepository.save(new WorkerRate(worker, new BigDecimal(hourlyRate), effectiveFrom, Instant.now()));
    }
}

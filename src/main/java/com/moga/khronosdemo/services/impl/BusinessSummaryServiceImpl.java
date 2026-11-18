package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.response.BusinessSummaryResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.dtos.response.ExpenseResponseDTO;
import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.services.BusinessSummaryService;
import com.moga.khronosdemo.services.CashClosingService;
import com.moga.khronosdemo.services.ExpenseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sales, expenses, staff cost and margin of an establishment over a range of business days, and the same
 * figures for the period of equal length right before it. Both periods are loaded together in a fixed
 * number of queries (the establishment, its closings, its expenses and the four of StaffWorkLoader),
 * however long the range, and added up in memory by BusinessPeriod. Also the CSV exports of the
 * summary, the cash closings and the expenses.
 */
@Service
@Transactional(readOnly = true)
public class BusinessSummaryServiceImpl implements BusinessSummaryService {

    private final EstablishmentRepository establishmentRepository;
    private final CashClosingRepository cashClosingRepository;
    private final ExpenseRepository expenseRepository;
    private final StaffWorkLoader staffWorkLoader;
    private final CashClosingService cashClosingService;
    private final ExpenseService expenseService;
    private final Clock clock;

    public BusinessSummaryServiceImpl(EstablishmentRepository establishmentRepository,
                                      CashClosingRepository cashClosingRepository, ExpenseRepository expenseRepository,
                                      StaffWorkLoader staffWorkLoader, CashClosingService cashClosingService,
                                      ExpenseService expenseService, Clock clock) {
        this.establishmentRepository = establishmentRepository;
        this.cashClosingRepository = cashClosingRepository;
        this.expenseRepository = expenseRepository;
        this.staffWorkLoader = staffWorkLoader;
        this.cashClosingService = cashClosingService;
        this.expenseService = expenseService;
        this.clock = clock;
    }

    @Override
    public BusinessSummaryResponseDTO summary(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, ReportServiceImpl.MAX_DAYS);
        Establishment establishment = getEstablishment(establishmentId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), establishment.getZoneId());
        long length = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        LocalDate previousFrom = range.from().minusDays(length);
        LocalDate previousTo = range.from().minusDays(1);

        List<CashClosing> closings = cashClosingRepository.findByEstablishmentBetween(establishmentId, previousFrom, range.to());
        List<Expense> expenses = expenseRepository.findByEstablishmentBetween(establishmentId, previousFrom, range.to());
        StaffWork staff = staffWorkLoader.load(establishment, previousFrom, range.to());

        BusinessPeriod current = new BusinessPeriod(range.from(), range.to(), closings, expenses,
                staff.between(range.from(), range.to()), today);
        BusinessPeriod previous = new BusinessPeriod(previousFrom, previousTo, closings, expenses,
                staff.between(previousFrom, previousTo), today);
        return new BusinessSummaryResponseDTO(range.from(), range.to(), ReportServiceImpl.CURRENCY, current.totals(),
                current.days(), current.expensesByCategory(), previous.asPrevious());
    }

    // The closings as the listing shows them (a draft's figures follow its cash expenses), earliest day first
    @Override
    public byte[] cashClosingsCsv(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, ReportServiceImpl.MAX_DAYS);
        Establishment establishment = getEstablishment(establishmentId);
        List<CashClosingResponseDTO> closings =
                new ArrayList<>(cashClosingService.findByEstablishment(establishmentId, range.from(), range.to()));
        Collections.reverse(closings);
        return ReportCsvFormatter.cashClosings(closings, establishment.getZoneId());
    }

    // Oldest first, then in the order they were entered
    @Override
    public byte[] expensesCsv(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, ReportServiceImpl.MAX_DAYS);
        getEstablishment(establishmentId);
        List<ExpenseResponseDTO> expenses =
                new ArrayList<>(expenseService.findByEstablishment(establishmentId, range.from(), range.to(), null));
        Collections.reverse(expenses);
        return ReportCsvFormatter.expenses(expenses);
    }

    @Override
    public byte[] summaryCsv(Long establishmentId, LocalDate from, LocalDate to) {
        return ReportCsvFormatter.summary(summary(establishmentId, from, to));
    }

    private Establishment getEstablishment(Long id) {
        return establishmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", id));
    }
}

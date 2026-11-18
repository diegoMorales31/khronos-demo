package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.config.CashClosingProperties;
import com.moga.khronosdemo.dtos.request.CashClosingRequestDTO;
import com.moga.khronosdemo.dtos.response.CashClosingChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.CashClosingResponseDTO;
import com.moga.khronosdemo.dtos.response.ErrorCode;
import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.CashClosingChange;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.enums.CashClosingAction;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.CashClosingMapper;
import com.moga.khronosdemo.repositories.CashClosingChangeRepository;
import com.moga.khronosdemo.repositories.CashClosingRepository;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.ExpenseRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.CashClosingService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The daily cash closing. Every change to an establishment's closings, like every change to its
 * expenses, takes its lock through CashRecordLock: the establishment's row first, and only then the
 * closing, so two requests for the same day take turns and the second one sees what the first one
 * committed: two closings for one day can't both be created, and a cash expense can't slip into a day
 * while it is being closed. The unique constraint on (establishment, business day) is the last line of
 * defence and also answers 409.
 */
@Service
@Transactional
@EnableConfigurationProperties(CashClosingProperties.class)
public class CashClosingServiceImpl implements CashClosingService {

    private static final BigDecimal NO_EXPENSES = BigDecimal.ZERO.setScale(2);
    private static final String UNIQUE_DAY_CONSTRAINT = "uk_cash_closings_establishment_business_date";

    private final CashClosingRepository cashClosingRepository;
    private final CashClosingChangeRepository cashClosingChangeRepository;
    private final EstablishmentRepository establishmentRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CashRecordLock cashRecordLock;
    private final CashClosingMapper cashClosingMapper;
    private final CashClosingProperties properties;
    private final Clock clock;

    public CashClosingServiceImpl(CashClosingRepository cashClosingRepository,
                                  CashClosingChangeRepository cashClosingChangeRepository,
                                  EstablishmentRepository establishmentRepository, ExpenseRepository expenseRepository,
                                  UserRepository userRepository, CashRecordLock cashRecordLock,
                                  CashClosingMapper cashClosingMapper, CashClosingProperties properties, Clock clock) {
        this.cashClosingRepository = cashClosingRepository;
        this.cashClosingChangeRepository = cashClosingChangeRepository;
        this.establishmentRepository = establishmentRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.cashRecordLock = cashRecordLock;
        this.cashClosingMapper = cashClosingMapper;
        this.properties = properties;
        this.clock = clock;
    }

    // Latest day first, over at most 366 days (see DateRange.capped): without dates, the last 366 up to today.
    // The drafts' cash expenses are added up in one query, whatever the number of days
    @Override
    @Transactional(readOnly = true)
    public List<CashClosingResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.capped(from, to, ReportServiceImpl.MAX_DAYS, () -> today(establishmentId));
        List<CashClosing> closings = cashClosingRepository.findByEstablishment(establishmentId, range.from(), range.to());
        Map<LocalDate, BigDecimal> cashExpenses = cashExpensesOfDrafts(establishmentId, closings);
        return closings.stream()
                .map(closing -> cashClosingMapper.toResponse(closing,
                        closing.figures(cashExpenses.getOrDefault(closing.getBusinessDate(), NO_EXPENSES))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CashClosingResponseDTO findById(Long id) {
        CashClosing closing = cashClosingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CashClosing", id));
        return cashClosingMapper.toResponse(closing, currentFigures(closing));
    }

    @Override
    public CashClosingResponseDTO create(CashClosingRequestDTO request, Long actorId) {
        Establishment establishment = cashRecordLock.lockEstablishment(request.establishmentId());
        LocalDate businessDate = request.businessDate();
        if (businessDate.isAfter(today(establishment))) {
            throw new BusinessRuleException("The business date cannot be in the future", ErrorCode.FUTURE_DATE);
        }
        if (cashClosingRepository.existsByEstablishment_IdAndBusinessDate(establishment.getId(), businessDate)) {
            throw alreadyExists(businessDate);
        }

        CashClosing closing = new CashClosing(establishment, businessDate, auditNow());
        cashClosingMapper.updateEntity(closing, request);
        try {
            cashClosingRepository.saveAndFlush(closing);
        } catch (DataIntegrityViolationException ex) {
            // Only reachable if a closing for the day was inserted without the establishment's lock
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(UNIQUE_DAY_CONSTRAINT)) {
                throw alreadyExists(businessDate);
            }
            throw ex;
        }
        return record(closing, CashClosingAction.CREATED, actorId, null);
    }

    // The establishment and the business day never change: the ones in the request are ignored
    @Override
    public CashClosingResponseDTO update(Long id, CashClosingRequestDTO request, Long actorId) {
        CashClosing closing = cashRecordLock.lockCashClosing(id);
        if (closing.isClosed()) {
            throw new BusinessRuleException("A closed cash closing cannot be edited; reopen it first",
                    ErrorCode.CASH_CLOSING_CLOSED);
        }
        cashClosingMapper.updateEntity(closing, request);
        closing.setUpdatedAt(auditNow());
        return record(closing, CashClosingAction.UPDATED, actorId, null);
    }

    // Freezes the figures as they are now. The cash has to be counted first, and a difference over the
    // threshold, either way, needs a reason
    @Override
    public CashClosingResponseDTO close(Long id, String differenceReason, Long actorId) {
        CashClosing closing = cashRecordLock.lockCashClosing(id);
        if (closing.isClosed()) {
            throw new BusinessRuleException("The cash closing is already closed", ErrorCode.CASH_CLOSING_CLOSED);
        }
        if (!closing.isCounted()) {
            throw new BusinessRuleException("The cash has not been counted; enter countedCash before closing the day",
                    ErrorCode.CASH_NOT_COUNTED);
        }
        CashClosing.Figures figures = closing.figures(cashExpensesOf(closing));
        String reason = clean(differenceReason);
        BigDecimal threshold = properties.differenceThreshold();
        if (reason == null && figures.difference().abs().compareTo(threshold) > 0) {
            throw new BusinessRuleException("The cash difference of " + figures.difference().toPlainString()
                    + " EUR is larger than the " + threshold.setScale(2).toPlainString()
                    + " EUR allowed without a reason; send a differenceReason to close the day",
                    ErrorCode.DIFFERENCE_REASON_REQUIRED);
        }
        closing.close(figures, reason, getUser(actorId), auditNow());
        return record(closing, CashClosingAction.CLOSED, actorId, reason);
    }

    // Back to a draft, whose figures follow the day's cash expenses again
    @Override
    public CashClosingResponseDTO reopen(Long id, String reason, Long actorId) {
        CashClosing closing = cashRecordLock.lockCashClosing(id);
        if (!closing.isClosed()) {
            throw new BusinessRuleException("The cash closing is not closed", ErrorCode.CASH_CLOSING_NOT_CLOSED);
        }
        closing.reopen(auditNow());
        return record(closing, CashClosingAction.REOPENED, actorId, reason);
    }

    // Only a draft that was never closed can go, and its history with it. A day that was closed once
    // stays on record: it can be reopened and corrected, not deleted.
    @Override
    public void delete(Long id) {
        CashClosing closing = cashRecordLock.lockCashClosing(id);
        if (closing.isClosed()) {
            throw new BusinessRuleException("A closed cash closing cannot be deleted; reopen it to correct it instead",
                    ErrorCode.CASH_CLOSING_CLOSED);
        }
        if (cashClosingChangeRepository.existsByCashClosing_IdAndAction(id, CashClosingAction.CLOSED)) {
            throw new BusinessRuleException("A cash closing that has been closed before cannot be deleted;"
                    + " correct it and close it again instead", ErrorCode.CASH_CLOSING_WAS_CLOSED);
        }
        cashClosingChangeRepository.deleteByCashClosing_Id(id);
        cashClosingRepository.delete(closing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashClosingChangeResponseDTO> findHistory(Long id) {
        if (!cashClosingRepository.existsById(id)) {
            throw new ResourceNotFoundException("CashClosing", id);
        }
        return cashClosingChangeRepository.findByCashClosing_IdOrderByChangedAtAscIdAsc(id).stream()
                .map(cashClosingMapper::toResponse)
                .toList();
    }

    // Adds the history row with the figures right after the change and returns the closing as it is now
    private CashClosingResponseDTO record(CashClosing closing, CashClosingAction action, Long actorId, String reason) {
        CashClosing.Figures figures = currentFigures(closing);
        cashClosingChangeRepository.save(new CashClosingChange(closing, action, auditNow(), getUser(actorId),
                clean(reason), figures));
        return cashClosingMapper.toResponse(closing, figures);
    }

    // A closed day has its figures stored; a draft needs the cash expenses recorded for its day so far
    private CashClosing.Figures currentFigures(CashClosing closing) {
        return closing.figures(closing.isClosed() ? null : cashExpensesOf(closing));
    }

    private BigDecimal cashExpensesOf(CashClosing closing) {
        LocalDate day = closing.getBusinessDate();
        return expenseRepository.sumByDay(closing.getEstablishment().getId(), PaymentMethod.CASH, day, day).stream()
                .findFirst()
                .map(ExpenseRepository.DayTotal::getTotal)
                .orElse(NO_EXPENSES);
    }

    private Map<LocalDate, BigDecimal> cashExpensesOfDrafts(Long establishmentId, List<CashClosing> closings) {
        List<LocalDate> draftDays = closings.stream()
                .filter(closing -> !closing.isClosed())
                .map(CashClosing::getBusinessDate)
                .toList();
        if (draftDays.isEmpty()) {
            return Map.of();
        }
        LocalDate first = draftDays.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate last = draftDays.stream().max(Comparator.naturalOrder()).orElseThrow();
        return expenseRepository.sumByDay(establishmentId, PaymentMethod.CASH, first, last).stream()
                .collect(Collectors.toMap(ExpenseRepository.DayTotal::getBusinessDate, ExpenseRepository.DayTotal::getTotal));
    }

    private static BusinessRuleException alreadyExists(LocalDate businessDate) {
        return new BusinessRuleException("A cash closing already exists for " + businessDate
                + " at this establishment; edit that one instead", ErrorCode.CASH_CLOSING_EXISTS,
                Map.of("date", businessDate.toString()));
    }

    // "Today" is the establishment's local date
    private LocalDate today(Establishment establishment) {
        return LocalDate.ofInstant(clock.instant(), establishment.getZoneId());
    }

    private LocalDate today(Long establishmentId) {
        return today(establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId)));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // Audit times keep the precision PostgreSQL stores, so a response matches what is read back later
    private Instant auditNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String clean(String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }
}

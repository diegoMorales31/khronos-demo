package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.dtos.request.ClockInRequestDTO;
import com.moga.khronosdemo.dtos.request.ClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryClockOutRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryCorrectionRequestDTO;
import com.moga.khronosdemo.dtos.request.TimeEntryRequestDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryChangeResponseDTO;
import com.moga.khronosdemo.dtos.response.TimeEntryResponseDTO;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.TimeEntryChange;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.enums.TimeEntryAction;
import com.moga.khronosdemo.entities.enums.TimeEntrySource;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.TimeEntryMapper;
import com.moga.khronosdemo.repositories.TimeEntryChangeRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.services.TimeEntryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TimeEntryServiceImpl implements TimeEntryService {

    static final Duration MAX_DURATION = Duration.ofHours(24);

    // A manager's device may run slightly ahead of the server; an instant this close still counts as "now"
    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryChangeRepository timeEntryChangeRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final TimeEntryShiftLinker shiftLinker;
    private final TimeEntryMapper timeEntryMapper;
    private final Clock clock;

    public TimeEntryServiceImpl(TimeEntryRepository timeEntryRepository,
                                TimeEntryChangeRepository timeEntryChangeRepository,
                                WorkerRepository workerRepository, UserRepository userRepository,
                                TimeEntryShiftLinker shiftLinker, TimeEntryMapper timeEntryMapper, Clock clock) {
        this.timeEntryRepository = timeEntryRepository;
        this.timeEntryChangeRepository = timeEntryChangeRepository;
        this.workerRepository = workerRepository;
        this.userRepository = userRepository;
        this.shiftLinker = shiftLinker;
        this.timeEntryMapper = timeEntryMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TimeEntryResponseDTO> findOpenOfWorker(Long workerId) {
        return timeEntryRepository.findOpenByWorker(workerId).map(timeEntryMapper::toResponse);
    }

    // Every change to a worker's entries holds that worker's row lock until commit (the same lock as
    // their schedule changes), so two clock-ins sent at once take turns and the second sees the first.
    // The partial unique index on time_entries is the last line of defence.
    @Override
    public TimeEntryResponseDTO clockIn(Long workerId, ClockInRequestDTO request) {
        Worker worker = lockOwnWorker(workerId);
        if (!worker.getActive()) {
            throw new BusinessRuleException("Worker is not active");
        }
        if (timeEntryRepository.findOpenByWorker(workerId).isPresent()) {
            throw new BusinessRuleException("You already have an open time entry");
        }
        Instant now = now();
        checkNoOverlap(workerId, now, null, null);

        TimeEntry entry = new TimeEntry(worker, now, TimeEntrySource.WORKER, clean(request != null ? request.note() : null),
                auditNow());
        shiftLinker.link(entry);
        timeEntryRepository.save(entry);
        record(entry, TimeEntryAction.CREATED, worker.getUser(), null);
        return timeEntryMapper.toResponse(entry);
    }

    @Override
    public TimeEntryResponseDTO clockOut(Long workerId, ClockOutRequestDTO request) {
        lockOwnWorker(workerId);
        TimeEntry entry = timeEntryRepository.findOpenByWorker(workerId)
                .orElseThrow(() -> new BusinessRuleException("You have no open time entry"));
        closeNow(entry, request != null ? request.breakMinutes() : null,
                "This time entry has been open for more than 24 hours; ask a manager to correct it");
        String note = clean(request != null ? request.note() : null);
        if (note != null) {
            entry.setNote(note);
        }
        record(entry, TimeEntryAction.CLOCKED_OUT, entry.getWorker().getUser(), null);
        return timeEntryMapper.toResponse(entry);
    }

    @Override
    public TimeEntryResponseDTO create(TimeEntryRequestDTO request, Long actorId) {
        Worker worker = lockWorker(request.workerId());
        Instant clockIn = toSeconds(request.clockIn());
        Instant clockOut = toSeconds(request.clockOut());
        int breakMinutes = request.breakMinutes() != null ? request.breakMinutes() : 0;
        checkTimes(clockIn, clockOut, breakMinutes);
        checkNoOverlap(worker.getUserId(), clockIn, clockOut, null);

        TimeEntry entry = new TimeEntry(worker, clockIn, TimeEntrySource.MANAGER, null, auditNow());
        entry.setClockOut(clockOut);
        entry.setBreakMinutes(breakMinutes);
        shiftLinker.link(entry);
        timeEntryRepository.save(entry);
        record(entry, TimeEntryAction.CREATED, getUser(actorId), request.reason());
        return timeEntryMapper.toResponse(entry);
    }

    @Override
    public TimeEntryResponseDTO correct(Long id, TimeEntryCorrectionRequestDTO request, Long actorId) {
        TimeEntry entry = lockAndGet(id);
        if (entry.isVoided()) {
            throw new BusinessRuleException("A voided time entry cannot be corrected");
        }
        Instant clockIn = toSeconds(request.clockIn());
        Instant clockOut = toSeconds(request.clockOut());
        checkTimes(clockIn, clockOut, request.breakMinutes());
        checkNoOverlap(entry.getWorker().getUserId(), clockIn, clockOut, entry.getId());

        entry.setClockIn(clockIn);
        entry.setClockOut(clockOut);
        entry.setBreakMinutes(request.breakMinutes());
        entry.setEdited(true);
        // A new clock-in may belong to another shift, or to none
        shiftLinker.link(entry);
        record(entry, TimeEntryAction.CORRECTED, getUser(actorId), request.reason());
        return timeEntryMapper.toResponse(entry);
    }

    // An owner or admin closes a worker's open entry, for example when the worker left without clocking
    // out. As for the worker, the clock-out is the server's time; the history keeps who did it and why.
    @Override
    public TimeEntryResponseDTO clockOutEntry(Long id, TimeEntryClockOutRequestDTO request, Long actorId) {
        TimeEntry entry = lockAndGet(id);
        if (entry.isVoided()) {
            throw new BusinessRuleException("A voided time entry cannot be clocked out");
        }
        if (!entry.isOpen()) {
            throw new BusinessRuleException("The time entry is already clocked out");
        }
        closeNow(entry, request.breakMinutes(), "This time entry has been open for more than 24 hours; correct it"
                + " with PUT /api/time-entries/" + id + " and the real clock-out time");
        record(entry, TimeEntryAction.CLOCKED_OUT, getUser(actorId), request.reason());
        return timeEntryMapper.toResponse(entry);
    }

    // Voiding keeps the entry and its history but leaves it out of listings (unless asked for) and totals
    @Override
    public TimeEntryResponseDTO voidEntry(Long id, String reason, Long actorId) {
        TimeEntry entry = lockAndGet(id);
        if (entry.isVoided()) {
            throw new BusinessRuleException("The time entry is already voided");
        }
        entry.setVoidedAt(auditNow());
        record(entry, TimeEntryAction.VOIDED, getUser(actorId), reason);
        return timeEntryMapper.toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryChangeResponseDTO> findHistory(Long id) {
        if (!timeEntryRepository.existsById(id)) {
            throw new ResourceNotFoundException("TimeEntry", id);
        }
        return timeEntryChangeRepository.findByTimeEntry_IdOrderByChangedAtAscIdAsc(id).stream()
                .map(timeEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> findByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeVoided) {
        DateRange range = new DateRange(from, to);
        return timeEntryRepository.findByWorker(workerId, range.from(), range.to(), includeVoided).stream()
                .map(timeEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> findByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                          boolean includeVoided) {
        DateRange range = new DateRange(from, to);
        return timeEntryRepository.findByEstablishment(establishmentId, range.from(), range.to(), includeVoided)
                .stream()
                .map(timeEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> findOpenByEstablishment(Long establishmentId) {
        return timeEntryRepository.findOpenByEstablishment(establishmentId).stream()
                .map(timeEntryMapper::toResponse)
                .toList();
    }

    // Clocks an open entry out at the server's time. A null break keeps the entry's current one. No entry
    // lasts more than 24 hours, so past that it has to be corrected with the real clock-out time instead.
    // checkTimes keeps an open entry's break within the time since its clock-in, so a kept break fits; an
    // entry saved before that rule may still have a longer one, and then the clock-out has to give the
    // real break instead of failing on a break nobody sent.
    private void closeNow(TimeEntry entry, Integer breakMinutes, String tooLongMessage) {
        Instant now = now();
        if (Duration.between(entry.getClockIn(), now).compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException(tooLongMessage);
        }
        if (breakMinutes == null && entry.getBreakMinutes() > Duration.between(entry.getClockIn(), now).toMinutes()) {
            throw new BusinessRuleException("The saved break is longer than the time since clock-in;"
                    + " send breakMinutes with the clock-out");
        }
        int breakToKeep = breakMinutes != null ? breakMinutes : entry.getBreakMinutes();
        checkTimes(entry.getClockIn(), now, breakToKeep);

        entry.setClockOut(now);
        entry.setBreakMinutes(breakToKeep);
    }

    // Rules shared by clock-out, manual entries and corrections. clockOut is null for an open entry, whose
    // break cannot be longer than the time since its clock-in so far. That time only grows, so a clock-out
    // that keeps the break, without sending one, always fits it.
    private void checkTimes(Instant clockIn, Instant clockOut, int breakMinutes) {
        Instant latest = clock.instant().plus(CLOCK_SKEW);
        if (clockIn.isAfter(latest)) {
            throw new BusinessRuleException("Clock-in cannot be in the future");
        }
        if (clockOut == null) {
            if (breakMinutes > Duration.between(clockIn, now()).toMinutes()) {
                throw new BusinessRuleException("The break cannot be longer than the time since clock-in");
            }
            return;
        }
        if (!clockOut.isAfter(clockIn)) {
            throw new BusinessRuleException("Clock-out must be after clock-in");
        }
        if (clockOut.isAfter(latest)) {
            throw new BusinessRuleException("Clock-out cannot be in the future");
        }
        Duration length = Duration.between(clockIn, clockOut);
        if (length.compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException("A time entry cannot last more than 24 hours");
        }
        if (breakMinutes > length.toMinutes()) {
            throw new BusinessRuleException("The break cannot be longer than the time between clock-in and clock-out");
        }
    }

    private void checkNoOverlap(Long workerId, Instant clockIn, Instant clockOut, Long excludeId) {
        if (timeEntryRepository.existsOverlapping(workerId, clockIn, clockOut, excludeId)) {
            throw new BusinessRuleException("The time entry overlaps another time entry of this worker");
        }
    }

    private void record(TimeEntry entry, TimeEntryAction action, User author, String reason) {
        timeEntryChangeRepository.save(new TimeEntryChange(entry, action, auditNow(), author, author.displayName(),
                clean(reason)));
    }

    // The worker acting on their own entries; a WORKER account that is not linked to a venue cannot clock in
    private Worker lockOwnWorker(Long workerId) {
        return workerRepository.lockById(workerId)
                .orElseThrow(() -> new BusinessRuleException("Your account is not linked to an establishment"));
    }

    private Worker lockWorker(Long workerId) {
        return workerRepository.lockById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
    }

    // Takes the worker's lock before reading the entry, so the checks see any change committed meanwhile
    private TimeEntry lockAndGet(Long id) {
        Long workerId = timeEntryRepository.findWorkerIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", id));
        lockWorker(workerId);
        return timeEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimeEntry", id));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // Clock-in and clock-out are kept to the second
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static Instant toSeconds(Instant instant) {
        return instant != null ? instant.truncatedTo(ChronoUnit.SECONDS) : null;
    }

    // Audit times keep the precision PostgreSQL stores, so a response matches what is read back later
    private Instant auditNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String clean(String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }
}

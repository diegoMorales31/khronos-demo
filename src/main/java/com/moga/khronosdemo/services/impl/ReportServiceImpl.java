package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.config.TimeTrackingProperties;
import com.moga.khronosdemo.dtos.response.HoursReportResponseDTO;
import com.moga.khronosdemo.dtos.response.HoursTotalsResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkDayResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerDaysReportResponseDTO;
import com.moga.khronosdemo.dtos.response.WorkerHoursResponseDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.mappers.TimeEntryMapper;
import com.moga.khronosdemo.mappers.WorkerShiftMapper;
import com.moga.khronosdemo.repositories.EstablishmentRepository;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.WorkerRateRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import com.moga.khronosdemo.services.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Planned against worked hours and staff costs. Each report loads everything it needs in a fixed
 * number of queries (workers, shifts, entries, rates) and adds it up in memory, however many workers
 * or days the range holds. The establishment's report reads them through StaffWork, as the business
 * summary does, so both work out staff costs the same way.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    static final int MAX_DAYS = 366;
    static final String CURRENCY = "EUR";

    private static final Comparator<Worker> BY_NAME = Comparator
            .comparing((Worker worker) -> Objects.toString(worker.getUser().getFirstName(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(worker -> Objects.toString(worker.getUser().getLastName(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Worker::getUserId);

    private final EstablishmentRepository establishmentRepository;
    private final WorkerRepository workerRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final WorkerRateRepository workerRateRepository;
    private final StaffWorkLoader staffWorkLoader;
    private final WorkerShiftMapper workerShiftMapper;
    private final TimeEntryMapper timeEntryMapper;
    private final TimeTrackingProperties properties;
    private final Clock clock;

    public ReportServiceImpl(EstablishmentRepository establishmentRepository, WorkerRepository workerRepository,
                             WorkerShiftRepository workerShiftRepository, TimeEntryRepository timeEntryRepository,
                             WorkerRateRepository workerRateRepository, StaffWorkLoader staffWorkLoader,
                             WorkerShiftMapper workerShiftMapper, TimeEntryMapper timeEntryMapper,
                             TimeTrackingProperties properties, Clock clock) {
        this.establishmentRepository = establishmentRepository;
        this.workerRepository = workerRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.workerRateRepository = workerRateRepository;
        this.staffWorkLoader = staffWorkLoader;
        this.workerShiftMapper = workerShiftMapper;
        this.timeEntryMapper = timeEntryMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public HoursReportResponseDTO hoursByEstablishment(Long establishmentId, LocalDate from, LocalDate to) {
        DateRange range = DateRange.bounded(from, to, MAX_DAYS);
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId));
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, establishment.getZoneId());

        StaffWork staff = staffWorkLoader.load(establishment, range.from(), range.to());
        // Inactive workers only appear when they have something in the range
        List<Worker> workers = new ArrayList<>(staff.listedWorkers());
        workers.sort(BY_NAME);
        List<WorkerHoursResponseDTO> rows = workers.stream()
                .map(worker -> row(worker, staff.tallyOf(worker), staff.ratesOf(worker), today, now))
                .toList();
        return new HoursReportResponseDTO(range.from(), range.to(), CURRENCY, rows, totals(rows, staff));
    }

    @Override
    public WorkerDaysReportResponseDTO daysByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeCosts) {
        DateRange range = DateRange.bounded(from, to, MAX_DAYS);
        Worker worker = workerRepository.findWithUserAndEstablishmentById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        ZoneId zone = worker.getEstablishment().getZoneId();
        Instant now = clock.instant();

        WorkTally whole = new WorkTally(zone);
        Map<LocalDate, WorkTally> days = new TreeMap<>();
        for (WorkerShift shift : workerShiftRepository.findByWorker(workerId, range.from(), range.to())) {
            whole.add(shift);
            days.computeIfAbsent(shift.getDate(), day -> new WorkTally(zone)).add(shift);
        }
        for (TimeEntry entry : timeEntryRepository.findCountedByWorker(workerId, range.from(), range.to())) {
            whole.add(entry);
            days.computeIfAbsent(entry.getBusinessDate(), day -> new WorkTally(zone)).add(entry);
        }
        // A worker reading their own report never gets rates or costs, so they are not even loaded
        RateSchedule rates = includeCosts
                ? RateSchedule.of(workerRateRepository.findByWorker_UserIdOrderByEffectiveFromDesc(workerId))
                : RateSchedule.none();

        List<WorkDayResponseDTO> dayRows = new ArrayList<>();
        days.forEach((date, tally) -> dayRows.add(new WorkDayResponseDTO(
                date,
                tally.plannedMinutes(),
                tally.workedMinutes(),
                tally.dayStatus(now, properties.lateThreshold()),
                tally.shifts().stream().map(workerShiftMapper::toResponse).toList(),
                tally.entries().stream().map(timeEntryMapper::toResponse).toList())));

        User user = worker.getUser();
        long planned = whole.plannedMinutes();
        long worked = whole.workedMinutes();
        return new WorkerDaysReportResponseDTO(
                worker.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                range.from(),
                range.to(),
                planned,
                worked,
                worked - planned,
                includeCosts ? rates.costOf(whole.plannedMinutesByDay()) : null,
                includeCosts ? rates.costOf(whole.workedMinutesByDay()) : null,
                dayRows);
    }

    @Override
    public byte[] hoursCsv(Long establishmentId, LocalDate from, LocalDate to) {
        return ReportCsvFormatter.hours(hoursByEstablishment(establishmentId, from, to));
    }

    // The time-tracking record: one row per entry, with times in the establishment's time zone
    @Override
    public byte[] timeEntriesCsvByEstablishment(Long establishmentId, LocalDate from, LocalDate to,
                                                boolean includeVoided) {
        DateRange range = DateRange.bounded(from, to, MAX_DAYS);
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Establishment", establishmentId));
        List<TimeEntry> entries = timeEntryRepository.findByEstablishment(establishmentId, range.from(), range.to(),
                includeVoided);
        return ReportCsvFormatter.timeEntries(entries, establishment.getZoneId());
    }

    @Override
    public byte[] timeEntriesCsvByWorker(Long workerId, LocalDate from, LocalDate to, boolean includeVoided) {
        DateRange range = DateRange.bounded(from, to, MAX_DAYS);
        Worker worker = workerRepository.findWithUserAndEstablishmentById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        List<TimeEntry> entries = timeEntryRepository.findByWorker(workerId, range.from(), range.to(), includeVoided);
        return ReportCsvFormatter.timeEntries(entries, worker.getEstablishment().getZoneId());
    }

    private WorkerHoursResponseDTO row(Worker worker, WorkTally tally, RateSchedule rates, LocalDate today, Instant now) {
        User user = worker.getUser();
        long planned = tally.plannedMinutes();
        long worked = tally.workedMinutes();
        return new WorkerHoursResponseDTO(
                worker.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                worker.getActive(),
                planned,
                worked,
                worked - planned,
                tally.shifts().size(),
                tally.entries().size(),
                tally.lateCount(properties.lateThreshold()),
                tally.missedShiftsCount(now),
                tally.unplannedCount(),
                tally.openEntriesCount(),
                rates.rateOn(today),
                rates.costOf(tally.plannedMinutesByDay()),
                rates.costOf(tally.workedMinutesByDay()));
    }

    // Costs add up the workers' known costs, as the business summary's staff cost does; null when none is known.
    // costPartial says that a known actual cost leaves out someone who worked.
    private static HoursTotalsResponseDTO totals(List<WorkerHoursResponseDTO> rows, StaffWork staff) {
        long planned = rows.stream().mapToLong(WorkerHoursResponseDTO::plannedMinutes).sum();
        long worked = rows.stream().mapToLong(WorkerHoursResponseDTO::workedMinutes).sum();
        BigDecimal actualCost = staff.actualCost();
        int workersWithoutCost = staff.workersWithoutActualCost();
        return new HoursTotalsResponseDTO(planned, worked, worked - planned, staff.plannedCost(), actualCost,
                actualCost != null && workersWithoutCost > 0, workersWithoutCost);
    }
}

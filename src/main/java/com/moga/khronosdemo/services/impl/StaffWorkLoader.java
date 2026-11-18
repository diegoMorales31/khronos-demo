package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.repositories.TimeEntryRepository;
import com.moga.khronosdemo.repositories.WorkerRateRepository;
import com.moga.khronosdemo.repositories.WorkerRepository;
import com.moga.khronosdemo.repositories.WorkerShiftRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Loads an establishment's {@link StaffWork} in four queries: its workers with their accounts, the
 * shifts dated in the range, the entries that count (not voided) whose business day is in the range,
 * and every rate of its workers. It runs inside the caller's transaction.
 */
@Component
class StaffWorkLoader {

    private final WorkerRepository workerRepository;
    private final WorkerShiftRepository workerShiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final WorkerRateRepository workerRateRepository;

    StaffWorkLoader(WorkerRepository workerRepository, WorkerShiftRepository workerShiftRepository,
                    TimeEntryRepository timeEntryRepository, WorkerRateRepository workerRateRepository) {
        this.workerRepository = workerRepository;
        this.workerShiftRepository = workerShiftRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.workerRateRepository = workerRateRepository;
    }

    // from and to are both included
    StaffWork load(Establishment establishment, LocalDate from, LocalDate to) {
        Long establishmentId = establishment.getId();
        return new StaffWork(
                establishment.getZoneId(),
                workerRepository.findWithUserByEstablishment(establishmentId),
                workerShiftRepository.findByEstablishmentBetween(establishmentId, from, to),
                timeEntryRepository.findCountedByEstablishment(establishmentId, from, to),
                RateSchedule.byWorker(workerRateRepository.findByEstablishment(establishmentId)));
    }
}

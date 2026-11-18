package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkerShiftRepository extends JpaRepository<WorkerShift, Long> {

    // The listings only map the worker and zone ids, which need no extra query.
    // from and to are inclusive, and a null one leaves that side of the range open. The casts
    // tell PostgreSQL the type of a null date parameter, which it cannot work out on its own.
    @Query("""
            select s from WorkerShift s
            where s.worker.establishment.owner.id = :ownerId
              and (cast(:from as LocalDate) is null or s.date >= :from)
              and (cast(:to as LocalDate) is null or s.date <= :to)
            order by s.date, s.startTime, s.id
            """)
    List<WorkerShift> findByOwner(@Param("ownerId") Long ownerId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query("""
            select s from WorkerShift s
            where s.worker.userId = :workerId
              and (cast(:from as LocalDate) is null or s.date >= :from)
              and (cast(:to as LocalDate) is null or s.date <= :to)
            order by s.date, s.startTime, s.id
            """)
    List<WorkerShift> findByWorker(@Param("workerId") Long workerId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
            select s from WorkerShift s
            where s.zone.id = :zoneId
              and (cast(:from as LocalDate) is null or s.date >= :from)
              and (cast(:to as LocalDate) is null or s.date <= :to)
            order by s.date, s.startTime, s.id
            """)
    List<WorkerShift> findByZone(@Param("zoneId") Long zoneId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    // Every shift of the establishment's workers dated from 'from' to 'to', both included, for the reports
    @Query("""
            select s from WorkerShift s
            where s.worker.establishment.id = :establishmentId
              and s.date >= :from and s.date <= :to
            order by s.date, s.startTime, s.id
            """)
    List<WorkerShift> findByEstablishmentBetween(@Param("establishmentId") Long establishmentId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    // The event's local date-time must fall inside the shift's real interval [start, end), so an
    // event at 01:00 on Saturday is covered by a Friday shift from 18:00 to 02:00.
    @Query("""
            select count(s) > 0 from WorkerShift s
            where s.worker.userId = :workerId
              and s.zone.id = :zoneId
              and s.startsAt <= :eventTime
              and s.endsAt > :eventTime
            """)
    boolean existsShiftCoveringEvent(@Param("workerId") Long workerId,
                                     @Param("zoneId") Long zoneId,
                                     @Param("eventTime") LocalDateTime eventTime);

    // The worker's shifts that are running at the given local time or start no later than latestStart,
    // earliest first. Shifts never overlap, so a shift that is running always comes first.
    @Query("""
            select s from WorkerShift s
            where s.worker.userId = :workerId
              and s.startsAt <= :latestStart
              and s.endsAt > :localTime
            order by s.startsAt
            """)
    List<WorkerShift> findShiftsForClockIn(@Param("workerId") Long workerId,
                                           @Param("localTime") LocalDateTime localTime,
                                           @Param("latestStart") LocalDateTime latestStart);

    // Reads only the worker's id, so the shift itself is first loaded once that worker is locked
    @Query("select s.worker.userId from WorkerShift s where s.id = :id")
    Optional<Long> findWorkerIdById(@Param("id") Long id);

    // Compares real intervals, so a shift can overlap shifts on the day before or after it
    @Query("""
            select count(s) > 0 from WorkerShift s
            where s.worker.userId = :workerId
              and s.startsAt < :endsAt
              and s.endsAt > :startsAt
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean existsOverlappingShift(@Param("workerId") Long workerId,
                                   @Param("startsAt") LocalDateTime startsAt,
                                   @Param("endsAt") LocalDateTime endsAt,
                                   @Param("excludeId") Long excludeId);
}

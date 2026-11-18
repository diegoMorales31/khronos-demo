package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    // Listings fetch the worker and their account with the entries, because the response shows the name.
    // from and to filter the business date, inclusive; a null one leaves that side open.

    // The open entry: no clock-out and not voided. The database allows at most one per worker
    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user
            where w.userId = :workerId and t.clockOut is null and t.voidedAt is null
            """)
    Optional<TimeEntry> findOpenByWorker(@Param("workerId") Long workerId);

    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user
            where t.establishment.id = :establishmentId and t.clockOut is null and t.voidedAt is null
            order by t.clockIn, t.id
            """)
    List<TimeEntry> findOpenByEstablishment(@Param("establishmentId") Long establishmentId);

    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user
            where w.userId = :workerId
              and (cast(:from as LocalDate) is null or t.businessDate >= :from)
              and (cast(:to as LocalDate) is null or t.businessDate <= :to)
              and (:includeVoided = true or t.voidedAt is null)
            order by t.clockIn, t.id
            """)
    List<TimeEntry> findByWorker(@Param("workerId") Long workerId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("includeVoided") boolean includeVoided);

    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user
            where t.establishment.id = :establishmentId
              and (cast(:from as LocalDate) is null or t.businessDate >= :from)
              and (cast(:to as LocalDate) is null or t.businessDate <= :to)
              and (:includeVoided = true or t.voidedAt is null)
            order by t.clockIn, t.id
            """)
    List<TimeEntry> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("includeVoided") boolean includeVoided);

    // The entries that count in a report (not voided), with the shift each one was matched to
    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user left join fetch t.shift
            where t.establishment.id = :establishmentId
              and t.voidedAt is null
              and t.businessDate >= :from and t.businessDate <= :to
            order by t.clockIn, t.id
            """)
    List<TimeEntry> findCountedByEstablishment(@Param("establishmentId") Long establishmentId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    @Query("""
            select t from TimeEntry t join fetch t.worker w join fetch w.user left join fetch t.shift
            where w.userId = :workerId
              and t.voidedAt is null
              and t.businessDate >= :from and t.businessDate <= :to
            order by t.clockIn, t.id
            """)
    List<TimeEntry> findCountedByWorker(@Param("workerId") Long workerId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Query("select t.worker.userId from TimeEntry t where t.id = :id")
    Optional<Long> findWorkerIdById(@Param("id") Long id);

    // Voided entries count too: they are part of the record. These keep workers, venues and owners with
    // entries from being deleted
    boolean existsByWorker_UserId(Long workerId);

    boolean existsByEstablishment_Id(Long establishmentId);

    boolean existsByEstablishment_Owner_Id(Long ownerId);

    // Entries are [clockIn, clockOut) intervals, and an open one runs on with no end. A null end
    // checks a new open entry. Voided entries never overlap anything.
    @Query("""
            select count(t) > 0 from TimeEntry t
            where t.worker.userId = :workerId
              and t.voidedAt is null
              and (:excludeId is null or t.id <> :excludeId)
              and (cast(:end as Instant) is null or t.clockIn < :end)
              and (t.clockOut is null or t.clockOut > :start)
            """)
    boolean existsOverlapping(@Param("workerId") Long workerId,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("excludeId") Long excludeId);

    // The workers' entries that no shift was matched to, clocked in from 'from' (included) to 'to', for
    // the shifts just created. Voided entries are left alone.
    @Query("""
            select t from TimeEntry t
            where t.worker.userId in :workerIds
              and t.shift is null
              and t.voidedAt is null
              and t.clockIn >= :from and t.clockIn < :to
            """)
    List<TimeEntry> findUnmatchedByWorkers(@Param("workerIds") Collection<Long> workerIds,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    // After a shift is edited, its entries that no longer match it (another worker now, or a clock-in
    // outside the shift's matching window) lose the link
    @Modifying(flushAutomatically = true)
    @Query("""
            update TimeEntry t set t.shift = null
            where t.shift.id = :shiftId
              and (t.worker.userId <> :workerId or t.clockIn < :windowStart or t.clockIn >= :windowEnd)
            """)
    int unlinkFromShift(@Param("shiftId") Long shiftId,
                        @Param("workerId") Long workerId,
                        @Param("windowStart") Instant windowStart,
                        @Param("windowEnd") Instant windowEnd);

    // The entries still matched to an edited shift follow its date
    @Modifying(flushAutomatically = true)
    @Query("update TimeEntry t set t.businessDate = :businessDate where t.shift.id = :shiftId")
    int moveToBusinessDate(@Param("shiftId") Long shiftId, @Param("businessDate") LocalDate businessDate);
}

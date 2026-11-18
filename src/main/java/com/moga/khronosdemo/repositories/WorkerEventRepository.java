package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkerEventRepository extends JpaRepository<WorkerEvent, Long> {

    List<WorkerEvent> findByWorker_UserId(Long workerId);

    List<WorkerEvent> findByEvent_Id(Long eventId);

    boolean existsByWorker_UserIdAndEvent_Id(Long workerId, Long eventId);

    @Query("select we.worker.userId from WorkerEvent we where we.event.id = :eventId")
    List<Long> findWorkerIdsByEventId(@Param("eventId") Long eventId);

    // The worker's assignments to events in either of two ranges of local time, each [from, until), that no
    // shift of the worker covers: none in the event's zone has started by the event's time and is still
    // running (the same test as WorkerShiftRepository.existsShiftCoveringEvent). The event and its zone
    // come with each assignment, for the notification.
    @Query("""
            select we from WorkerEvent we join fetch we.event e join fetch e.zone z
            where we.worker.userId = :workerId
              and (e.dateTime >= :from and e.dateTime < :until
                   or e.dateTime >= :otherFrom and e.dateTime < :otherUntil)
              and not exists (
                  select s.id from WorkerShift s
                  where s.worker.userId = :workerId
                    and s.zone = z
                    and s.startsAt <= e.dateTime
                    and s.endsAt > e.dateTime)
            order by e.dateTime, we.id
            """)
    List<WorkerEvent> findUncoveredByWorkerBetween(@Param("workerId") Long workerId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("until") LocalDateTime until,
                                                   @Param("otherFrom") LocalDateTime otherFrom,
                                                   @Param("otherUntil") LocalDateTime otherUntil);

    // Assignments to the establishment's events, filtered on the event's date-time and, unless
    // workerId is null, on the worker. A null bound leaves that side of the range open; the casts
    // give PostgreSQL its type.
    @Query("""
            select we from WorkerEvent we join we.event e
            where e.zone.establishment.id = :establishmentId
              and (:workerId is null or we.worker.userId = :workerId)
              and (cast(:from as LocalDateTime) is null or e.dateTime >= :from)
              and (cast(:until as LocalDateTime) is null or e.dateTime < :until)
            order by e.dateTime, we.id
            """)
    List<WorkerEvent> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                          @Param("workerId") Long workerId,
                                          @Param("from") LocalDateTime from,
                                          @Param("until") LocalDateTime until);
}

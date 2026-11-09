package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface WorkerShiftRepository extends JpaRepository<WorkerShift, Long> {

    List<WorkerShift> findByWorker_UserId(Long workerId);

    List<WorkerShift> findByZone_Id(Long zoneId);

    List<WorkerShift> findByWorker_Establishment_Owner_IdOrderByDateAscStartTimeAsc(Long ownerId);

    @Query("""
            select count(s) > 0 from WorkerShift s
            where s.worker.userId = :workerId
              and s.zone.id = :zoneId
              and s.date = :date
              and s.startTime <= :eventTime
              and s.endTime > :eventTime
            """)
    boolean existsShiftCoveringEvent(@Param("workerId") Long workerId,
                                     @Param("zoneId") Long zoneId,
                                     @Param("date") LocalDate date,
                                     @Param("eventTime") LocalTime eventTime);

    @Query("""
            select count(s) > 0 from WorkerShift s
            where s.worker.userId = :workerId
              and s.date = :date
              and s.startTime < :endTime
              and s.endTime > :startTime
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean existsOverlappingShift(@Param("workerId") Long workerId,
                                   @Param("date") LocalDate date,
                                   @Param("startTime") LocalTime startTime,
                                   @Param("endTime") LocalTime endTime,
                                   @Param("excludeId") Long excludeId);
}

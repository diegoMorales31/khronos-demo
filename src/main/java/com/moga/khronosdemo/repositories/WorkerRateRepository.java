package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkerRateRepository extends JpaRepository<WorkerRate, Long> {

    List<WorkerRate> findByWorker_UserIdOrderByEffectiveFromDesc(Long workerId);

    Optional<WorkerRate> findByIdAndWorker_UserId(Long id, Long workerId);

    boolean existsByWorker_UserIdAndEffectiveFrom(Long workerId, LocalDate effectiveFrom);

    // Every rate of the establishment's workers, for the reports, in one query
    @Query("""
            select r from WorkerRate r
            where r.worker.establishment.id = :establishmentId
            order by r.worker.userId, r.effectiveFrom
            """)
    List<WorkerRate> findByEstablishment(@Param("establishmentId") Long establishmentId);
}

package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Worker;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findByEstablishment_Id(Long establishmentId);

    // For the reports, which show each worker's name
    @Query("select w from Worker w join fetch w.user where w.establishment.id = :establishmentId order by w.userId")
    List<Worker> findWithUserByEstablishment(@Param("establishmentId") Long establishmentId);

    @Query("select w from Worker w join fetch w.user join fetch w.establishment where w.userId = :id")
    Optional<Worker> findWithUserAndEstablishmentById(@Param("id") Long id);

    // Locks the workers' rows until the transaction ends. Rows are locked in id order, so two
    // requests that touch overlapping sets of workers wait for each other instead of deadlocking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Worker w where w.userId in :ids order by w.userId")
    List<Worker> lockAllById(@Param("ids") Collection<Long> ids);

    // Locks one worker's row in the same way; empty when there is no such worker
    default Optional<Worker> lockById(Long id) {
        return lockAllById(List.of(id)).stream().findFirst();
    }
}

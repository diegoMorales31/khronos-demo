package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Establishment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    // Locks the establishment's row until the transaction ends. Every change to its cash closings and
    // expenses takes this lock first, so closing a day and adding a cash expense to it take turns.
    // Receiving a purchase order takes it too, before the order and its products, since it may create an
    // expense (see StockLedger for the lock order). On PostgreSQL, Hibernate 7.4 renders PESSIMISTIC_WRITE
    // as "for no key update of <alias>" (pinned by EstablishmentLockTests): it still makes two cash changes
    // wait for each other, but not the inserts whose foreign key points at the establishment (time entries,
    // zones, workers, stock movements...), whose check only takes FOR KEY SHARE. A plain FOR UPDATE would
    // make a clock-in wait while the till is being closed. Nothing else locks establishments.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Establishment e where e.id = :id")
    Optional<Establishment> lockById(@Param("id") Long id);

    List<Establishment> findByOwner_Id(Long ownerId);

    boolean existsByOwner_Id(Long ownerId);

    boolean existsByEstablishmentAddress(String establishmentAddress);

    boolean existsByEstablishmentAddressAndIdNot(String establishmentAddress, Long id);
}

package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.CashClosing;
import com.moga.khronosdemo.entities.enums.CashClosingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long> {

    // Latest business day first. from and to are inclusive; a null one leaves that side of the range open
    @Query("""
            select c from CashClosing c
            where c.establishment.id = :establishmentId
              and (cast(:from as LocalDate) is null or c.businessDate >= :from)
              and (cast(:to as LocalDate) is null or c.businessDate <= :to)
            order by c.businessDate desc
            """)
    List<CashClosing> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    // Every closing from 'from' to 'to', both included, earliest first, for the summary and the CSV export
    @Query("""
            select c from CashClosing c
            where c.establishment.id = :establishmentId
              and c.businessDate >= :from and c.businessDate <= :to
            order by c.businessDate
            """)
    List<CashClosing> findByEstablishmentBetween(@Param("establishmentId") Long establishmentId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    boolean existsByEstablishment_IdAndBusinessDate(Long establishmentId, LocalDate businessDate);

    boolean existsByEstablishment_IdAndBusinessDateAndStatus(Long establishmentId, LocalDate businessDate,
                                                            CashClosingStatus status);

    // Reads only the establishment's id, so the closing itself is first loaded once that establishment is locked
    @Query("select c.establishment.id from CashClosing c where c.id = :id")
    Optional<Long> findEstablishmentIdById(@Param("id") Long id);

    // Drafts count too. These keep establishments and owners with closings from being deleted
    boolean existsByEstablishment_Id(Long establishmentId);

    boolean existsByEstablishment_Owner_Id(Long ownerId);
}

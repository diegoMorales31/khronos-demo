package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.PurchaseOrder;
import com.moga.khronosdemo.entities.enums.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // Newest first, with their supplier, lines and products in one query. from and until bound the creation
    // time (until excluded); a null filter is left out
    @Query("""
            select o from PurchaseOrder o join fetch o.supplier left join fetch o.lines l left join fetch l.product
            where o.establishment.id = :establishmentId
              and (:status is null or o.status = :status)
              and (cast(:from as Instant) is null or o.createdAt >= :from)
              and (cast(:until as Instant) is null or o.createdAt < :until)
            order by o.createdAt desc, o.id desc
            """)
    List<PurchaseOrder> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                            @Param("status") PurchaseOrderStatus status,
                                            @Param("from") Instant from,
                                            @Param("until") Instant until);

    @Query("""
            select o from PurchaseOrder o join fetch o.supplier left join fetch o.lines l left join fetch l.product
            where o.id = :id
            """)
    Optional<PurchaseOrder> findWithLinesById(@Param("id") Long id);

    // Locks the order's row until the transaction ends, after the establishment's when that is needed and
    // before any product's (see StockLedger for the lock order)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);

    @Query("select o.establishment.id from PurchaseOrder o where o.id = :id")
    Optional<Long> findEstablishmentIdById(@Param("id") Long id);

    // The order whose receipt created the expense, if any (an expense records at most one order)
    @Query("select o.id from PurchaseOrder o where o.expense.id = :expenseId")
    Optional<Long> findIdByExpenseId(@Param("expenseId") Long expenseId);

    // Each of the establishment's expenses dated from 'from' to 'to', both included, that receiving one of
    // its orders created, with that order, in one query
    @Query("""
            select e.id as expenseId, o.id as purchaseOrderId from PurchaseOrder o join o.expense e
            where o.establishment.id = :establishmentId and e.date >= :from and e.date <= :to
            """)
    List<ExpenseOrder> findExpenseOrders(@Param("establishmentId") Long establishmentId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    boolean existsBySupplier_Id(Long supplierId);

    boolean existsByEstablishment_Id(Long establishmentId);

    boolean existsByEstablishment_Owner_Id(Long ownerId);

    boolean existsByLines_Product_Id(Long productId);

    interface ExpenseOrder {

        Long getExpenseId();

        Long getPurchaseOrderId();
    }
}

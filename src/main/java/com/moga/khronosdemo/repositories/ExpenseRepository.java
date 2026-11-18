package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Expense;
import com.moga.khronosdemo.entities.enums.ExpenseCategory;
import com.moga.khronosdemo.entities.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Newest first. from and to filter the business day, inclusive; a null filter is left out
    @Query("""
            select e from Expense e
            where e.establishment.id = :establishmentId
              and (cast(:from as LocalDate) is null or e.date >= :from)
              and (cast(:to as LocalDate) is null or e.date <= :to)
              and (:category is null or e.category = :category)
            order by e.date desc, e.id desc
            """)
    List<Expense> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("category") ExpenseCategory category);

    // Every expense from 'from' to 'to', both included, oldest first, for the summary and the CSV export
    @Query("""
            select e from Expense e
            where e.establishment.id = :establishmentId
              and e.date >= :from and e.date <= :to
            order by e.date, e.id
            """)
    List<Expense> findByEstablishmentBetween(@Param("establishmentId") Long establishmentId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    // What was paid with the given method on each day of the range that has any, in one query
    @Query("""
            select e.date as businessDate, sum(e.amount) as total from Expense e
            where e.establishment.id = :establishmentId
              and e.paymentMethod = :paymentMethod
              and e.date >= :from and e.date <= :to
            group by e.date
            """)
    List<DayTotal> sumByDay(@Param("establishmentId") Long establishmentId,
                            @Param("paymentMethod") PaymentMethod paymentMethod,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    @Query("select e.establishment.id from Expense e where e.id = :id")
    Optional<Long> findEstablishmentIdById(@Param("id") Long id);

    boolean existsByEstablishment_Id(Long establishmentId);

    boolean existsByEstablishment_Owner_Id(Long ownerId);

    interface DayTotal {

        LocalDate getBusinessDate();

        BigDecimal getTotal();
    }
}

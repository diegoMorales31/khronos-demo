package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.StockMovement;
import com.moga.khronosdemo.entities.enums.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // The stock of each of the establishment's products that has movements, in one query that reads only
    // the index on (establishment_id, product_id) including quantity (V15)
    @Query("""
            select m.product.id as productId, sum(m.quantity) as stock from StockMovement m
            where m.establishment.id = :establishmentId
            group by m.product.id
            """)
    List<ProductStock> sumByEstablishment(@Param("establishmentId") Long establishmentId);

    // The stock of each of these products that has movements, in one query
    @Query("""
            select m.product.id as productId, sum(m.quantity) as stock from StockMovement m
            where m.product.id in :productIds
            group by m.product.id
            """)
    List<ProductStock> sumByProducts(@Param("productIds") Collection<Long> productIds);

    // Newest business day first, then the latest recorded. from and to filter the business day, inclusive;
    // a null filter is left out
    @Query("""
            select m from StockMovement m join fetch m.product
            where m.establishment.id = :establishmentId
              and (cast(:from as LocalDate) is null or m.date >= :from)
              and (cast(:to as LocalDate) is null or m.date <= :to)
              and (:productId is null or m.product.id = :productId)
              and (:type is null or m.type = :type)
            order by m.date desc, m.createdAt desc, m.id desc
            """)
    List<StockMovement> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("productId") Long productId,
                                            @Param("type") StockMovementType type);

    // For each product and type with movements dated from 'from' to 'to', both included: the quantity and
    // the value added up, each movement's value being quantity x its unit cost rounded to cents, signed like
    // the quantity. The value is null when none of them had a cost. One query, however long the range
    @Query("""
            select m.product.id as productId, m.type as type, sum(m.quantity) as quantity,
                   sum(round(m.quantity * m.unitCost, 2)) as value
            from StockMovement m
            where m.establishment.id = :establishmentId and m.date >= :from and m.date <= :to
            group by m.product.id, m.type
            """)
    List<ProductTypeTotal> sumByProductAndType(@Param("establishmentId") Long establishmentId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    boolean existsByProduct_Id(Long productId);

    boolean existsByEstablishment_Id(Long establishmentId);

    boolean existsByEstablishment_Owner_Id(Long ownerId);

    interface ProductTypeTotal {

        Long getProductId();

        StockMovementType getType();

        BigDecimal getQuantity();

        BigDecimal getValue();
    }

    interface ProductStock {

        Long getProductId();

        BigDecimal getStock();
    }
}

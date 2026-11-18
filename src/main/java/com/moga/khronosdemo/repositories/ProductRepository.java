package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // With their suppliers, in one query. Inactive products only when asked for
    @Query("""
            select p from Product p left join fetch p.supplier
            where p.establishment.id = :establishmentId and (:includeInactive = true or p.active = true)
            """)
    List<Product> findByEstablishment(@Param("establishmentId") Long establishmentId,
                                      @Param("includeInactive") boolean includeInactive);

    @Query("select p from Product p left join fetch p.supplier where p.id = :id")
    Optional<Product> findWithSupplierById(@Param("id") Long id);

    // Compares names as the unique index does, on lower(name). excludedId leaves out the product being
    // renamed; null leaves out nothing
    @Query("""
            select count(p) > 0 from Product p
            where p.establishment.id = :establishmentId and lower(p.name) = lower(:name)
              and (:excludedId is null or p.id <> :excludedId)
            """)
    boolean existsByName(@Param("establishmentId") Long establishmentId, @Param("name") String name,
                         @Param("excludedId") Long excludedId);

    // Locks the products' rows until the transaction ends, in id order, so two requests that touch
    // overlapping products wait for each other instead of deadlocking. Every new stock movement of a
    // product is recorded under this lock (see StockLedger). On PostgreSQL it is FOR NO KEY UPDATE, which
    // still lets other requests insert rows that reference the product.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllById(@Param("ids") Collection<Long> ids);

    // Every product of the establishment, locked in id order as above. Deleting the establishment takes
    // them, after the establishment's own lock, before it checks for movements and orders
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.establishment.id = :establishmentId order by p.id")
    List<Product> lockByEstablishment(@Param("establishmentId") Long establishmentId);

    // The products whose usual supplier this is, locked in id order as above. Deleting the supplier clears
    // it from them, so it takes them before it checks for orders
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.supplier.id = :supplierId order by p.id")
    List<Product> lockBySupplier(@Param("supplierId") Long supplierId);

    @Query("select p.establishment.id from Product p where p.id = :id")
    Optional<Long> findEstablishmentIdById(@Param("id") Long id);

    // Which establishment each of the products that exist belongs to, in one query
    @Query("select p.id as id, p.establishment.id as establishmentId from Product p where p.id in :ids")
    List<ProductEstablishment> findEstablishmentIdsByIdIn(@Param("ids") Collection<Long> ids);

    interface ProductEstablishment {

        Long getId();

        Long getEstablishmentId();
    }
}

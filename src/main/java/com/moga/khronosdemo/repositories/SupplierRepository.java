package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query("select s from Supplier s where s.establishment.id = :establishmentId order by lower(s.name), s.id")
    List<Supplier> findByEstablishment(@Param("establishmentId") Long establishmentId);

    // Compares names as the unique index does, on lower(name). excludedId leaves out the supplier being
    // renamed; null leaves out nothing
    @Query("""
            select count(s) > 0 from Supplier s
            where s.establishment.id = :establishmentId and lower(s.name) = lower(:name)
              and (:excludedId is null or s.id <> :excludedId)
            """)
    boolean existsByName(@Param("establishmentId") Long establishmentId, @Param("name") String name,
                         @Param("excludedId") Long excludedId);

    @Query("select s.establishment.id from Supplier s where s.id = :id")
    Optional<Long> findEstablishmentIdById(@Param("id") Long id);
}

package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByEstablishment_Id(Long establishmentId);

    boolean existsByNameAndEstablishment_Id(String name, Long establishmentId);

    boolean existsByNameAndEstablishment_IdAndIdNot(String name, Long establishmentId, Long id);
}

package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    List<Establishment> findByOwner_Id(Long ownerId);

    boolean existsByOwner_Id(Long ownerId);

    boolean existsByEstablishmentAddress(String establishmentAddress);

    boolean existsByEstablishmentAddressAndIdNot(String establishmentAddress, Long id);
}

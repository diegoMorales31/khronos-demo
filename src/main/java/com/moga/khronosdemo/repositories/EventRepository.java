package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByZone_Id(Long zoneId);

    List<Event> findByZone_Establishment_Id(Long establishmentId);
}

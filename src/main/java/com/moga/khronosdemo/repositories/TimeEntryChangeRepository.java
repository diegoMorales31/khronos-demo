package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.TimeEntryChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeEntryChangeRepository extends JpaRepository<TimeEntryChange, Long> {

    // Oldest first; the id breaks ties between changes made in the same instant
    List<TimeEntryChange> findByTimeEntry_IdOrderByChangedAtAscIdAsc(Long timeEntryId);
}

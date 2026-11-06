package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerShiftRepository extends JpaRepository<WorkerShift, Long> {

    List<WorkerShift> findByWorker_UserId(Long workerId);

    List<WorkerShift> findByZone_Id(Long zoneId);

    List<WorkerShift> findByWorker_Establishment_Owner_IdOrderByDateAscStartTimeAsc(Long ownerId);
}

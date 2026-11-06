package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.WorkerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerEventRepository extends JpaRepository<WorkerEvent, Long> {

    List<WorkerEvent> findByWorker_UserId(Long workerId);

    List<WorkerEvent> findByEvent_Id(Long eventId);

    boolean existsByWorker_UserIdAndEvent_Id(Long workerId, Long eventId);
}

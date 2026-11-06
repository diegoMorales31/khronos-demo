package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findByEstablishment_Id(Long establishmentId);
}

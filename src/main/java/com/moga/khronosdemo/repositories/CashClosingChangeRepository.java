package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.CashClosingChange;
import com.moga.khronosdemo.entities.enums.CashClosingAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashClosingChangeRepository extends JpaRepository<CashClosingChange, Long> {

    // Oldest first; the id breaks ties between changes made in the same instant
    List<CashClosingChange> findByCashClosing_IdOrderByChangedAtAscIdAsc(Long cashClosingId);

    boolean existsByCashClosing_IdAndAction(Long cashClosingId, CashClosingAction action);

    // Only for a draft that was never closed, which is deleted together with its history
    void deleteByCashClosing_Id(Long cashClosingId);
}

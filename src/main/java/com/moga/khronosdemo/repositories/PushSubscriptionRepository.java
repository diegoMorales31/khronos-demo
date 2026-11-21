package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUser_IdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.user.id = :userId and s.endpoint = :endpoint")
    int deleteByUserIdAndEndpoint(Long userId, String endpoint);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.user.id = :userId")
    int deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(String endpoint);
}

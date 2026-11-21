package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {

    // Locked, so two requests with the same token can't both rotate it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s join fetch s.user where s.tokenHash = :hash")
    Optional<RefreshSession> findByTokenHashForUpdate(String hash);

    Optional<RefreshSession> findByPreviousHash(String hash);

    List<RefreshSession> findByUser_IdOrderByExpiresAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("delete from RefreshSession s where s.user.id = :userId")
    int deleteByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("delete from RefreshSession s where s.tokenHash = :hash")
    int deleteByTokenHash(String hash);

    @Modifying
    @Transactional
    @Query("delete from RefreshSession s where s.user.id = :userId and s.expiresAt <= :now")
    int deleteExpiredOf(Long userId, Instant now);
}

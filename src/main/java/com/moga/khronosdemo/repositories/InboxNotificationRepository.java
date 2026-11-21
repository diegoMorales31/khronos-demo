package com.moga.khronosdemo.repositories;

import com.moga.khronosdemo.entities.InboxNotification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InboxNotificationRepository extends JpaRepository<InboxNotification, Long> {

    // Newest first; ids break ties between notices of the same instant
    @Query("select n from InboxNotification n where n.user.id = :userId order by n.createdAt desc, n.id desc")
    List<InboxNotification> findLatest(Long userId, Limit limit);

    // The page after the notice with this id, for "show older"
    @Query("""
            select n from InboxNotification n
            where n.user.id = :userId
              and (n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))
            order by n.createdAt desc, n.id desc
            """)
    List<InboxNotification> findOlder(Long userId, Instant createdAt, Long id, Limit limit);

    Optional<InboxNotification> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndReadAtIsNull(Long userId);

    @Modifying
    @Transactional
    @Query("update InboxNotification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(Long userId, Instant now);

    @Modifying
    @Transactional
    @Query("delete from InboxNotification n where n.createdAt < :cutoff")
    int deleteCreatedBefore(Instant cutoff);
}

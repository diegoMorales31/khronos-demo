package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.response.NotificationPageResponseDTO;
import com.moga.khronosdemo.entities.enums.NotificationType;

public interface NotificationService {

    /** Adds a notice to the user's list, in the caller's transaction. data is turned into JSON. */
    void record(Long userId, NotificationType type, Object data);

    /** The user's newest notices, or those older than the notice before (an id of theirs) when given. */
    NotificationPageResponseDTO list(Long userId, Long before, int limit);

    long unreadCount(Long userId);

    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);

    /** Deletes the notices older than the retention; answers how many. */
    int deleteExpired();
}

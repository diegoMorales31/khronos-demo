package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.config.NotificationProperties;
import com.moga.khronosdemo.dtos.response.NotificationPageResponseDTO;
import com.moga.khronosdemo.dtos.response.NotificationResponseDTO;
import com.moga.khronosdemo.entities.InboxNotification;
import com.moga.khronosdemo.entities.enums.NotificationType;
import com.moga.khronosdemo.exceptions.ResourceNotFoundException;
import com.moga.khronosdemo.repositories.InboxNotificationRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

/**
 * Each user's in-app list of notices. Only its owner reads it or marks it read: someone else's
 * notice answers 404, as if it did not exist.
 */
@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final InboxNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationProperties properties;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NotificationServiceImpl(InboxNotificationRepository notificationRepository, UserRepository userRepository,
                                   NotificationProperties properties, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void record(Long userId, NotificationType type, Object data) {
        notificationRepository.save(new InboxNotification(userRepository.getReferenceById(userId), type,
                jsonMapper.writeValueAsString(data), clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponseDTO list(Long userId, Long before, int limit) {
        // One more than asked tells whether there are older ones
        Limit oneMore = Limit.of(limit + 1);
        List<InboxNotification> found;
        if (before == null) {
            found = notificationRepository.findLatest(userId, oneMore);
        } else {
            InboxNotification after = getOwn(userId, before);
            found = notificationRepository.findOlder(userId, after.getCreatedAt(), after.getId(), oneMore);
        }
        boolean hasMore = found.size() > limit;
        List<NotificationResponseDTO> items = found.stream().limit(limit).map(this::toResponse).toList();
        return new NotificationPageResponseDTO(items, notificationRepository.countByUser_IdAndReadAtIsNull(userId), hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUser_IdAndReadAtIsNull(userId);
    }

    // Reading it again keeps the first time it was read
    @Override
    public void markRead(Long userId, Long notificationId) {
        InboxNotification notification = getOwn(userId, notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
    }

    @Override
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, clock.instant());
    }

    // Every night at 04:00 server time
    @Override
    @Scheduled(cron = "0 0 4 * * *")
    public int deleteExpired() {
        int deleted = notificationRepository.deleteCreatedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} notifications older than {}", deleted, properties.retention());
        }
        return deleted;
    }

    private InboxNotification getOwn(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
    }

    private NotificationResponseDTO toResponse(InboxNotification notification) {
        return new NotificationResponseDTO(notification.getId(), notification.getType(),
                jsonMapper.readTree(notification.getData()), notification.getCreatedAt(), notification.getReadAt());
    }
}

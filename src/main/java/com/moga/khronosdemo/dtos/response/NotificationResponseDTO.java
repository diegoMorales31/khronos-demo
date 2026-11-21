package com.moga.khronosdemo.dtos.response;

import com.moga.khronosdemo.entities.enums.NotificationType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** One notice; data's shape depends on type (see NotificationType). readAt is null while unread. */
public record NotificationResponseDTO(Long id, NotificationType type, JsonNode data, Instant createdAt, Instant readAt) {
}

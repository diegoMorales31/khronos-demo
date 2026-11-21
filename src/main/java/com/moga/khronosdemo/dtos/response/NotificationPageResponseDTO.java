package com.moga.khronosdemo.dtos.response;

import java.util.List;

/** Notices newest first; hasMore when there are older ones (ask again with before = the last id). */
public record NotificationPageResponseDTO(List<NotificationResponseDTO> items, long unreadCount, boolean hasMore) {
}

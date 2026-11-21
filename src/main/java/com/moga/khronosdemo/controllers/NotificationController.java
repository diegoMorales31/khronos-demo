package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.response.NotificationPageResponseDTO;
import com.moga.khronosdemo.dtos.response.UnreadCountResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own in-app notifications: there is no way to read anyone else's. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponseDTO list(@RequestParam(required = false) Long before,
                                            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return notificationService.list(principal.getId(), before, limit);
    }

    // Cheap enough to ask every minute, for the badge on the menu
    @GetMapping("/unread-count")
    public UnreadCountResponseDTO unreadCount(@AuthenticationPrincipal SecurityUser principal) {
        return new UnreadCountResponseDTO(notificationService.unreadCount(principal.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal SecurityUser principal) {
        notificationService.markRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal SecurityUser principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.noContent().build();
    }
}

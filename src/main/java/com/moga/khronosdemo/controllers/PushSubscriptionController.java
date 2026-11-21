package com.moga.khronosdemo.controllers;

import com.moga.khronosdemo.dtos.request.PushSubscriptionRequestDTO;
import com.moga.khronosdemo.dtos.response.PushPublicKeyResponseDTO;
import com.moga.khronosdemo.security.SecurityUser;
import com.moga.khronosdemo.services.PushService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Push notifications on the caller's own devices. Workers are the ones notified (their shifts and events),
 * so only they can turn it on; anyone can take a device of theirs off.
 */
@RestController
@RequestMapping("/api/push-subscriptions")
public class PushSubscriptionController {

    private final PushService pushService;

    public PushSubscriptionController(PushService pushService) {
        this.pushService = pushService;
    }

    // 204 when push is not set up on this server
    @GetMapping("/public-key")
    public ResponseEntity<PushPublicKeyResponseDTO> publicKey() {
        return pushService.publicKey()
                .map(key -> ResponseEntity.ok(new PushPublicKeyResponseDTO(key)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequestDTO request,
                                          @AuthenticationPrincipal SecurityUser principal) {
        pushService.subscribe(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint, @AuthenticationPrincipal SecurityUser principal) {
        pushService.unsubscribe(principal.getId(), endpoint);
        return ResponseEntity.noContent().build();
    }
}

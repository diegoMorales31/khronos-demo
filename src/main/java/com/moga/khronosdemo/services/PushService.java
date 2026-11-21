package com.moga.khronosdemo.services;

import com.moga.khronosdemo.dtos.request.PushSubscriptionRequestDTO;
import com.moga.khronosdemo.notifications.PushMessage;

import java.util.Optional;

public interface PushService {

    /** The VAPID public key, empty when push is off. */
    Optional<String> publicKey();

    void subscribe(Long userId, PushSubscriptionRequestDTO request);

    void unsubscribe(Long userId, String endpoint);

    /** Forgets every device of the user, so nothing more reaches them until they sign in and turn it on again. */
    void unsubscribeAll(Long userId);

    /** Sends to every device of the user. Never throws: a failed push only skips that device. */
    void send(Long userId, PushMessage message);
}

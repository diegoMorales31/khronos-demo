package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.Worker;

// userId finds the worker's in-app list and the devices a push goes to
public record Recipient(Long userId) {

    public static Recipient of(Worker worker) {
        return new Recipient(worker.getUserId());
    }
}

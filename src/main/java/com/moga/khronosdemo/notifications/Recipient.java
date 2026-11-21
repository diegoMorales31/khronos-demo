package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;

// userId finds the devices a push goes to; email and firstName make the email
public record Recipient(Long userId, String email, String firstName) {

    public static Recipient of(Worker worker) {
        User user = worker.getUser();
        return new Recipient(worker.getUserId(), user.getEmail(), user.getFirstName());
    }
}

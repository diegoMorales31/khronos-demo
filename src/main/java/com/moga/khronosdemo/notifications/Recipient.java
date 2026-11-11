package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;

public record Recipient(String email, String firstName) {

    public static Recipient of(Worker worker) {
        User user = worker.getUser();
        return new Recipient(user.getEmail(), user.getFirstName());
    }
}

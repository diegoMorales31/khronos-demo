package com.moga.khronosdemo.notifications;

public record EventChangedNotification(Recipient recipient, EventDetails before, EventDetails after) {
}

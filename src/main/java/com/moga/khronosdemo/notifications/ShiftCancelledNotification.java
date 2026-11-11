package com.moga.khronosdemo.notifications;

public record ShiftCancelledNotification(Recipient recipient, ShiftDetails shift) {
}

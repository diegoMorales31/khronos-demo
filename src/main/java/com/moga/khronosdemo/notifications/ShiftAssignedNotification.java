package com.moga.khronosdemo.notifications;

public record ShiftAssignedNotification(Recipient recipient, ShiftDetails shift) {
}

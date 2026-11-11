package com.moga.khronosdemo.notifications;

public record ShiftChangedNotification(Recipient recipient, ShiftDetails before, ShiftDetails after) {
}

package com.moga.khronosdemo.entities.enums;

/** What an in-app notification is about; the shape of its data depends on it (see InboxNotificationWriter). */
public enum NotificationType {
    /** data: { shifts: [shift], events: [event] } */
    ASSIGNED,
    /** data: { before: shift, after: shift } */
    SHIFT_CHANGED,
    /** data: { shift: shift } */
    SHIFT_CANCELLED,
    /** data: { before: event, after: event } */
    EVENT_CHANGED,
    /** data: { event: event, reason: EVENT_CANCELLED | NO_COVERING_SHIFT | null } */
    EVENT_UNASSIGNED
}

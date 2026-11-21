package com.moga.khronosdemo.notifications;

/** What the service worker shows: a title, the text below it and the app page a tap opens. */
public record PushMessage(String title, String body, String url) {

    // A tap opens the in-app list, where the same notice is waiting
    public static PushMessage toNotifications(String title, String body) {
        return new PushMessage(title, body, "/notifications");
    }
}

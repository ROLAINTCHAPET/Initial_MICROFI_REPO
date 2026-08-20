package com.microfi.notifications.domain;

/** Delivery channel for a {@link NotificationLog} entry. Only SMS is wired up today; PUSH is reserved for the mobile app's future push-notification support (architecture.txt: "Flash SMS, push, receipt triggers"). */
public enum NotificationChannel {
    SMS,
    PUSH
}

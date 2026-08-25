package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Notification;
import com.ams.hrms.service.NotificationService;
import com.ams.hrms.util.UiThread;

/** View-controller for notifications (spec section 41); calls run off the EDT. */
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void load(boolean onlyUnread, Consumer<List<Notification>> onSuccess) {
        UiThread.executeAsync("Load notifications",
                () -> notificationService.list(onlyUnread), onSuccess);
    }

    public void unreadCount(Consumer<Long> onSuccess) {
        UiThread.executeAsync("Count unread notifications",
                () -> notificationService.unreadCount(), onSuccess);
    }

    /** Marks one notification read; {@code onChanged} fires only when state changed. */
    public void markRead(long notificationId, Runnable onChanged, Runnable onUnchanged) {
        UiThread.executeAsync("Mark notification read",
                () -> notificationService.markRead(notificationId),
                changed -> (changed ? onChanged : onUnchanged).run());
    }

    public void markAllRead(Runnable onDone) {
        UiThread.executeAsync("Mark all notifications read",
                () -> notificationService.markAllRead(),
                changed -> onDone.run());
    }

    /** Runs the daily operational scan (startup hook). */
    public void runScan(Consumer<NotificationService.ScanSummary> onSuccess) {
        UiThread.executeAsync("Notification operational scan",
                () -> notificationService.runOperationalScan(java.time.LocalDate.now()),
                onSuccess);
    }
}

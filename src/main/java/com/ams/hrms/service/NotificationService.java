package com.ams.hrms.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Notification;
import com.ams.hrms.repository.NotificationRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;

/**
 * Notification management (spec section 41). Three responsibilities:
 *
 * <ol>
 *   <li><b>Personal feed</b> - list / unread count / mark-read for the
 *       signed-in user; any authenticated user may read their own feed.</li>
 *   <li><b>Domain listeners</b> - subscribed once at bootstrap; leave and
 *       payroll events are fanned out to the right recipients on a daemon
 *       dispatcher thread (never on the EDT).</li>
 *   <li><b>Operational scan</b> - idempotent generation of pending-approval
 *       digests, document expiry warnings, birthday notices and training
 *       reminders. One-shot notices are deduplicated against any prior copy
 *       (read or unread) so marking one read keeps it dismissed; only the
 *       pending-work digest intentionally re-raises while it stays unread.</li>
 * </ol>
 */
public class NotificationService {

    public static final String DATA_SCOPE = "notifications";

    /** Stable reference keys used for deduplication. */
    public static final String REF_LEAVE_REQUEST = "LEAVE_REQUEST";
    public static final String REF_LEAVE_DIGEST = "LEAVE_DIGEST";
    public static final String REF_DOCUMENT = "EMPLOYEE_DOCUMENT";
    public static final String REF_BIRTHDAY = "EMPLOYEE_BIRTHDAY";
    public static final String REF_TRAINING_SESSION = "TRAINING_SESSION";
    public static final String REF_PAYROLL_CALCULATED = "PAYROLL_CALCULATED";
    public static final String REF_PAYROLL_PAID = "PAYROLL_PAID";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final int FEED_LIMIT = 200;
    private static final int PURGE_READ_AFTER_DAYS = 90;

    private final NotificationRepository repository;
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notification-dispatcher");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean scanRunning = new AtomicBoolean();

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    // Personal feed
    // ------------------------------------------------------------------

    /** Current user's feed, newest first. */
    public List<Notification> list(boolean onlyUnread) {
        requireAuthenticated();
        return repository.findForUser(SessionContext.currentUserId(), onlyUnread, FEED_LIMIT);
    }

    public long unreadCount() {
        requireAuthenticated();
        return repository.countUnread(SessionContext.currentUserId());
    }

    /** Marks one notification read; returns true when it was still unread. */
    public boolean markRead(long notificationId) {
        requireAuthenticated();
        boolean changed = repository.markRead(notificationId, SessionContext.currentUserId());
        if (changed) {
            publishChange();
        }
        return changed;
    }

    /** Marks every unread notification of the current user read. */
    public int markAllRead() {
        requireAuthenticated();
        int changed = repository.markAllRead(SessionContext.currentUserId());
        if (changed > 0) {
            publishChange();
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Fan-out API (also used by future modules)
    // ------------------------------------------------------------------

    /** Creates one notification for one recipient. */
    public void notifyUser(Long userId, String type, String title, String message,
                           String referenceModule, Long referenceId) {
        validateOrThrow(title, message, type, referenceModule);

        Notification notification = new Notification();
        notification.setUserId(Objects.requireNonNull(userId, "userId"));
        notification.setType(type);
        notification.setTitle(title.trim());
        notification.setMessage(message.trim());
        notification.setReferenceModule(emptyToNull(referenceModule));
        notification.setReferenceId(referenceId);
        repository.insert(notification);
        publishChange();
    }

    /**
     * Creates one notification per recipient. Returns how many rows were
     * inserted; publishes a single change event when any were created.
     */
    public int notifyUsers(List<Long> userIds, String type, String title, String message,
                           String referenceModule, Long referenceId) {
        validateOrThrow(title, message, type, referenceModule);
        int inserted = 0;
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title.trim());
            notification.setMessage(message.trim());
            notification.setReferenceModule(emptyToNull(referenceModule));
            notification.setReferenceId(referenceId);
            repository.insert(notification);
            inserted++;
        }
        if (inserted > 0) {
            publishChange();
        }
        return inserted;
    }

    public int notifyPermissionHolders(Permissions permission, String type, String title,
                                       String message, String referenceModule, Long referenceId) {
        return notifyUsers(repository.findUserIdsWithPermission(permission.name()),
                type, title, message, referenceModule, referenceId);
    }

    public int notifyAllUsers(String type, String title, String message,
                              String referenceModule, Long referenceId) {
        return notifyUsers(repository.findActiveUserIds(),
                type, title, message, referenceModule, referenceId);
    }

    // ------------------------------------------------------------------
    // Domain event listeners (registered once by ServiceRegistry)
    // ------------------------------------------------------------------

    /** Subscribes the notification handlers to application domain events. */
    public void registerDomainListeners() {
        EventBus.subscribe(Events.LeaveRequested.class, this::onLeaveRequested);
        EventBus.subscribe(Events.LeaveDecided.class, this::onLeaveDecided);
        EventBus.subscribe(Events.PayrollProcessed.class, this::onPayrollProcessed);
        LOG.info("Notification domain listeners registered");
    }

    private void onLeaveRequested(Events.LeaveRequested event) {
        dispatch(() -> notifyPermissionHolders(
                Permissions.LEAVE_APPROVE,
                Notification.TYPE_LEAVE,
                NotificationRules.leaveRequestedTitle(event.requesterName()),
                NotificationRules.leaveRequestedMessage(event.leaveCode(), event.requesterName()),
                REF_LEAVE_REQUEST,
                event.requestId()));
    }

    private void onLeaveDecided(Events.LeaveDecided event) {
        dispatch(() -> {
            Long recipient = repository.findUserIdLinkedToEmployee(event.employeeId());
            if (recipient == null) {
                LOG.debug("No user account linked to employee #{}; decision notice skipped",
                        event.employeeId());
                return;
            }
            notifyUser(recipient,
                    Notification.TYPE_LEAVE,
                    NotificationRules.leaveDecidedTitle(event.leaveCode(), event.approved()),
                    NotificationRules.leaveDecidedMessage(
                            event.leaveCode(), event.approved(),
                            event.decidedByName(), event.employeeId()),
                    REF_LEAVE_REQUEST,
                    event.requestId());
        });
    }

    private void onPayrollProcessed(Events.PayrollProcessed event) {
        dispatch(() -> {
            boolean paid = "PAID".equals(event.stage());
            notifyPermissionHolders(
                    Permissions.PAYROLL_VIEW,
                    Notification.TYPE_PAYROLL,
                    NotificationRules.payrollProcessedTitle(event.stage(), event.periodLabel()),
                    NotificationRules.payrollProcessedMessage(
                            event.stage(), event.periodLabel(), event.recordCount()),
                    paid ? REF_PAYROLL_PAID : REF_PAYROLL_CALCULATED,
                    null);
        });
    }

    // ------------------------------------------------------------------
    // Operational scan (idempotent)
    // ------------------------------------------------------------------

    /**
     * Generates the recurring notifications for {@code today}. Safe to run
     * repeatedly: one-shot items already delivered (read or unread) are
     * skipped, and the pending-leave digest is skipped while an unread copy
     * still exists.
     */
    public ScanSummary runOperationalScan(LocalDate today) {
        requireAuthenticated();
        if (!scanRunning.compareAndSet(false, true)) {
            return ScanSummary.EMPTY;
        }
        try {
            ScanSummary summary = new ScanSummary(
                    generatePendingLeaveDigests(),
                    generateDocumentAlerts(today),
                    generateBirthdayNotices(),
                    generateTrainingReminders());

            int purged = repository.purgeReadOlderThanDays(PURGE_READ_AFTER_DAYS);
            if (summary.total() > 0 || purged > 0) {
                LOG.info("Notification scan generated {} item(s) {} and purged {} read row(s)",
                        summary.total(), summary, purged);
            }
            if (summary.total() > 0) {
                publishChange();
            }
            return summary;
        } finally {
            scanRunning.set(false);
        }
    }

    public record ScanSummary(int leaveDigests, int documentAlerts,
                              int birthdayNotices, int trainingReminders) {

        public static final ScanSummary EMPTY = new ScanSummary(0, 0, 0, 0);

        public int total() {
            return leaveDigests + documentAlerts + birthdayNotices + trainingReminders;
        }
    }

    /** One digest per approver while requests are pending and unread. */
    private int generatePendingLeaveDigests() {
        long pending = repository.countPendingLeaveRequests();
        if (pending == 0) {
            return 0;
        }
        List<Long> recipients = new ArrayList<>(repository.findUserIdsWithPermission(
                Permissions.LEAVE_APPROVE.name()));
        recipients.removeIf(userId -> repository.existsUnread(
                userId, Notification.TYPE_LEAVE, REF_LEAVE_DIGEST, null));
        return notifyUsers(recipients,
                Notification.TYPE_WARNING,
                NotificationRules.leaveDigestTitle((int) pending),
                NotificationRules.leaveDigestMessage((int) pending),
                REF_LEAVE_DIGEST,
                null);
    }

    /** Expiring-document warnings to DOCUMENT_MANAGE holders. */
    private int generateDocumentAlerts(LocalDate today) {
        int warningDays = repository.intSetting("documents.expiry_warning_days",
                DocumentService.EXPIRY_WARNING_DAYS);
        List<Long> recipients = repository.findUserIdsWithPermission(
                Permissions.DOCUMENT_MANAGE.name());
        if (recipients.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (NotificationRepository.ExpiringDocument document
                : repository.findExpiringDocuments(warningDays)) {
            List<Long> pending = recipients.stream()
                    .filter(userId -> !repository.existsSimilar(userId,
                            Notification.TYPE_DOCUMENT, REF_DOCUMENT, document.documentId(),
                            null))
                    .toList();
            created += notifyUsers(pending,
                    Notification.TYPE_DOCUMENT,
                    NotificationRules.documentExpiryTitle(document.employeeName()),
                    NotificationRules.documentExpiryMessage(document.documentType(),
                            document.fileName(), document.expiryDate(), today),
                    REF_DOCUMENT,
                    document.documentId());
        }
        return created;
    }

    /** Birthday notices broadcast to all active users. */
    private int generateBirthdayNotices() {
        List<Long> allUsers = repository.findActiveUserIds();
        int created = 0;
        for (NotificationRepository.BirthdayEmployee birthday : repository.findBirthdays(LocalDate.now())) {
            // One shared recipient list; dedup removes users already notified.
            // Scoped to today so the notice can recur on future birthdays.
            List<Long> recipients = allUsers.stream()
                    .filter(userId -> !repository.existsSimilar(userId,
                            Notification.TYPE_INFO, REF_BIRTHDAY, birthday.employeeId(),
                            LocalDate.now().atStartOfDay()))
                    .toList();
            created += notifyUsers(recipients,
                    Notification.TYPE_INFO,
                    NotificationRules.birthdayTitle(birthday.fullName()),
                    NotificationRules.birthdayMessage(birthday.fullName(),
                            birthday.departmentName()),
                    REF_BIRTHDAY,
                    birthday.employeeId());
        }
        return created;
    }

    /** Upcoming-session reminders to enrolled employees' accounts. */
    private int generateTrainingReminders() {
        int created = 0;
        for (NotificationRepository.UpcomingSession session
                : repository.findUpcomingSessions(NotificationRules.TRAINING_REMINDER_DAYS)) {
            List<Long> recipients = repository.findEnrolledUserIds(session.sessionId()).stream()
                    .filter(userId -> !repository.existsSimilar(userId,
                            Notification.TYPE_TRAINING, REF_TRAINING_SESSION, session.sessionId(),
                            null))
                    .toList();
            created += notifyUsers(recipients,
                    Notification.TYPE_TRAINING,
                    NotificationRules.trainingReminderTitle(session.programName()),
                    NotificationRules.trainingReminderMessage(
                            session.programName(), session.startDateTime(), session.location()),
                    REF_TRAINING_SESSION,
                    session.sessionId());
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Runs listener work off the EDT; failures are logged, never fatal. */
    private void dispatch(Runnable work) {
        dispatcher.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                LOG.error("Notification dispatch failed: {}", e.getMessage(), e);
            }
        });
    }

    private void validateOrThrow(String title, String message, String type,
                                 String referenceModule) {
        List<String> errors = NotificationRules.validate(title, message, type, referenceModule);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void requireAuthenticated() {
        if (!SessionContext.isAuthenticated()) {
            throw new com.ams.hrms.exception.AuthenticationException(
                    "Notification access attempted without an active session",
                    "Please sign in first.");
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }
}

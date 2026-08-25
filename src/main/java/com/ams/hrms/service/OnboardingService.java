package com.ams.hrms.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.dto.OnboardingProgress;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.model.OnboardingTask;
import com.ams.hrms.repository.OnboardingRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Onboarding checklists (spec section 15): a fresh hire receives one task per
 * active template; HR works tasks through PENDING &rarr; COMPLETED / SKIPPED
 * / WAIVED with reopen support, progress is tracked as a completion
 * percentage, and templates are editable for future hires only - existing
 * checklists are never rewritten.
 */
public class OnboardingService {

    public static final String DATA_SCOPE = "onboarding";
    public static final int DEFAULT_DUE_DAYS = 14;

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingService.class);

    /** Legal task status transitions. */
    private static final Set<String> SETTLED_STATUSES =
            Set.of(OnboardingTask.STATUS_COMPLETED, OnboardingTask.STATUS_SKIPPED,
                    OnboardingTask.STATUS_WAIVED);

    private final OnboardingRepository repository;
    private final AuditService auditService;

    public OnboardingService(OnboardingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<OnboardingTask> tasksForEmployee(long employeeId) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        return repository.findByEmployee(employeeId);
    }

    public boolean hasChecklist(long employeeId) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        return repository.hasTasks(employeeId);
    }

    public List<Long> employeeIdsWithPendingTasks() {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        return repository.findEmployeeIdsWithPendingTasks();
    }

    public List<OnboardingTemplate> allTemplates() {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        return repository.findAllTemplates();
    }

    // ------------------------------------------------------------------
    // Checklist generation
    // ------------------------------------------------------------------

    /**
     * Creates one PENDING task per active template for {@code employeeId}.
     * Safe to call inside an active transaction (hire flow); refuses to run
     * twice for the same employee.
     *
     * @return number of tasks created
     */
    public int generateChecklist(long employeeId, LocalDate dueDate) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);

        List<OnboardingTemplate> templates = repository.findActiveTemplates();
        if (templates.isEmpty()) {
            throw new BusinessException("No onboarding templates",
                    "Activate at least one checklist template before generating a "
                            + "checklist.");
        }
        if (repository.hasTasks(employeeId)) {
            throw new BusinessException("Checklist already exists",
                    "This employee already has an onboarding checklist.");
        }

        if (TransactionManager.inTransaction()) {
            return insertChecklist(employeeId, dueDate);
        }
        return TransactionManager.execute(tx -> insertChecklist(employeeId, dueDate));
    }

    private int insertChecklist(long employeeId, LocalDate dueDate) {
        int created = repository.insertFromTemplates(
                new com.ams.hrms.repository.Sql(), employeeId, dueDate);
        audit("CREATE", "OnboardingTask", null,
                "Generated onboarding checklist with " + created + " task(s) for "
                        + "employee #" + employeeId);
        publishChange();
        LOG.debug("Onboarding checklist generated for employee #{} ({} tasks)",
                employeeId, created);
        return created;
    }

    // ------------------------------------------------------------------
    // Task transitions
    // ------------------------------------------------------------------

    /** Marks a pending task completed/skipped/waived; reopens when PENDING requested. */
    public void setTaskStatus(long taskId, String targetStatus) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        validateStatus(targetStatus);

        OnboardingTask task = findTask(taskId);
        if (targetStatus.equals(task.getStatus())) {
            return;
        }
        long userId = com.ams.hrms.security.SessionContext.currentUserId();
        repository.updateStatus(taskId, targetStatus, userId);
        audit("STATUS_CHANGE", "OnboardingTask", taskId,
                "Task '" + task.getTaskName() + "' for " + task.getEmployeeCode()
                        + " set to " + targetStatus);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Template management
    // ------------------------------------------------------------------

    /** Creates or updates a template; affects future checklists only. */
    public long saveTemplate(OnboardingTemplate template) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        validateTemplate(template);

        if (template.getId() == null) {
            template.setActive(true);
            long id = repository.insertTemplate(template);
            audit("CREATE", "OnboardingTemplate", id,
                    "Created onboarding template '" + template.getTaskName() + "'");
            publishChange();
            return id;
        }
        requireTemplate(template.getId());
        repository.updateTemplate(template);
        audit("UPDATE", "OnboardingTemplate", template.getId(),
                "Updated onboarding template '" + template.getTaskName() + "'");
        publishChange();
        return template.getId();
    }

    public void setTemplateActive(long templateId, boolean active) {
        SecurityService.require(Permissions.ONBOARDING_MANAGE);
        OnboardingTemplate template = requireTemplate(templateId);
        template.setActive(active);
        repository.updateTemplate(template);
        audit("STATUS_CHANGE", "OnboardingTemplate", templateId,
                "Template '" + template.getTaskName() + "' "
                        + (active ? "activated" : "deactivated"));
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validateStatus(String status) {
        if (!OnboardingTask.STATUS_PENDING.equals(status)
                && !SETTLED_STATUSES.contains(status)) {
            throw new ValidationException(List.of(
                    "Task status must be PENDING, COMPLETED, SKIPPED or WAIVED."));
        }
    }

    private void validateTemplate(OnboardingTemplate template) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, template.getTaskName(), "Task name");
        Validators.maxLength(errors, template.getTaskName(), 150, "Task name");
        Validators.maxLength(errors, template.getDescription(), 500, "Description");
        if (template.getTaskOrder() < 1 || template.getTaskOrder() > 999) {
            errors.add("Order must be between 1 and 999.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        template.setTaskName(Validators.normalize(template.getTaskName()));
        template.setDescription(Validators.normalize(template.getDescription()));
    }

    private OnboardingTask findTask(long taskId) {
        return repository.findTaskById(taskId).orElseThrow(() ->
                new BusinessException("Task not found",
                        "The onboarding task no longer exists."));
    }

    private OnboardingTemplate requireTemplate(long templateId) {
        return repository.findTemplateById(templateId).orElseThrow(() ->
                new BusinessException("Template not found",
                        "The onboarding template no longer exists."));
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "ONBOARDING", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.model.OnboardingTask;

/** Onboarding template + per-employee checklist persistence (spec section 15). */
public class OnboardingRepository {

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    private static final String SELECT_TEMPLATE =
            "SELECT id, task_name, description, task_order, is_mandatory, is_active "
                    + "FROM onboarding_templates";

    public List<OnboardingTemplate> findAllTemplates() {
        return new Sql().list(SELECT_TEMPLATE + " ORDER BY task_order, id",
                this::mapTemplate);
    }

    public List<OnboardingTemplate> findActiveTemplates() {
        return new Sql().list(SELECT_TEMPLATE + " WHERE is_active = 1 ORDER BY task_order, id",
                this::mapTemplate);
    }

    public Optional<OnboardingTemplate> findTemplateById(long id) {
        return new Sql().first(SELECT_TEMPLATE + " WHERE id = ?", this::mapTemplate, id);
    }

    public long insertTemplate(OnboardingTemplate template) {
        return new Sql().executeInsert(
                "INSERT INTO onboarding_templates (task_name, description, task_order, "
                        + "is_mandatory, is_active) VALUES (?, ?, ?, ?, ?)",
                template.getTaskName(), template.getDescription(), template.getTaskOrder(),
                template.isMandatory(), template.isActive());
    }

    public void updateTemplate(OnboardingTemplate template) {
        new Sql().executeUpdate(
                "UPDATE onboarding_templates SET task_name = ?, description = ?, "
                        + "task_order = ?, is_mandatory = ?, is_active = ? WHERE id = ?",
                template.getTaskName(), template.getDescription(), template.getTaskOrder(),
                template.isMandatory(), template.isActive(), template.getId());
    }

    // ------------------------------------------------------------------
    // Employee tasks
    // ------------------------------------------------------------------

    private static final String SELECT_TASK =
            "SELECT t.id, t.employee_id, t.template_task_id, t.task_name, t.task_order, "
                    + "t.due_date, t.status, t.completed_at, t.completed_by, "
                    + "u.full_name AS completed_by_name, tp.is_mandatory AS mandatory, "
                    + "e.employee_code, e.full_name AS employee_name "
                    + "FROM onboarding_tasks t "
                    + "JOIN employees e ON e.id = t.employee_id "
                    + "LEFT JOIN users u ON u.id = t.completed_by "
                    + "LEFT JOIN onboarding_templates tp ON tp.id = t.template_task_id";

    /** All tasks for one employee ordered by checklist position. */
    public List<OnboardingTask> findByEmployee(long employeeId) {
        return new Sql().list(SELECT_TASK + " WHERE t.employee_id = ? "
                        + "ORDER BY t.task_order, t.id",
                this::mapTask, employeeId);
    }

    public Optional<OnboardingTask> findTaskById(long taskId) {
        return new Sql().first(SELECT_TASK + " WHERE t.id = ?", this::mapTask, taskId);
    }

    /** True when the employee already has a generated checklist. */
    public boolean hasTasks(long employeeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM onboarding_tasks WHERE employee_id = ?", employeeId) > 0;
    }

    /** Employees that still have PENDING tasks (pickers / follow-up views). */
    public List<Long> findEmployeeIdsWithPendingTasks() {
        return new Sql().list(
                "SELECT DISTINCT employee_id FROM onboarding_tasks "
                        + "WHERE status IN ('PENDING', 'SKIPPED') ORDER BY employee_id",
                rs -> rs.getLong(1));
    }

    /**
     * Copies the active templates into a fresh checklist; runs inside the
     * caller's transaction when one is active (hire flow).
     */
    public int insertFromTemplates(Sql sql, long employeeId, LocalDate dueDate) {
        return sql.executeUpdate(
                "INSERT INTO onboarding_tasks (employee_id, template_task_id, task_name, "
                        + "task_order, due_date, status) "
                        + "SELECT ?, id, task_name, task_order, ?, 'PENDING' "
                        + "FROM onboarding_templates WHERE is_active = 1",
                employeeId, dueDate);
    }

    /** Moves one task to the given status, stamping completion metadata. */
    public void updateStatus(long taskId, String status, long userId) {
        boolean decided = !"PENDING".equals(status);
        new Sql().executeUpdate(
                "UPDATE onboarding_tasks SET status = ?, "
                        + "completed_at = " + (decided ? "NOW()" : "NULL") + ", "
                        + "completed_by = " + (decided ? "?" : "NULL") + " "
                        + "WHERE id = ?",
                decided ? new Object[]{status, userId, taskId}
                        : new Object[]{status, taskId});
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private OnboardingTemplate mapTemplate(ResultSet rs) throws SQLException {
        OnboardingTemplate template = new OnboardingTemplate();
        template.setId(rs.getLong("id"));
        template.setTaskName(rs.getString("task_name"));
        template.setDescription(rs.getString("description"));
        template.setTaskOrder(rs.getInt("task_order"));
        template.setMandatory(rs.getBoolean("is_mandatory"));
        template.setActive(rs.getBoolean("is_active"));
        return template;
    }

    private OnboardingTask mapTask(ResultSet rs) throws SQLException {
        OnboardingTask task = new OnboardingTask();
        task.setId(rs.getLong("id"));
        task.setEmployeeId(rs.getLong("employee_id"));
        long templateId = rs.getLong("template_task_id");
        task.setTemplateTaskId(rs.wasNull() ? null : templateId);
        task.setTaskName(rs.getString("task_name"));
        task.setTaskOrder(rs.getInt("task_order"));
        task.setDueDate(rs.getObject("due_date", LocalDate.class));
        task.setStatus(rs.getString("status"));
        task.setCompletedAt(rs.getObject("completed_at", LocalDateTime.class));
        long completedBy = rs.getLong("completed_by");
        task.setCompletedBy(rs.wasNull() ? null : completedBy);
        task.setCompletedByName(rs.getString("completed_by_name"));
        task.setMandatory(rs.getObject("mandatory") == null || rs.getBoolean("mandatory"));
        task.setEmployeeCode(rs.getString("employee_code"));
        task.setEmployeeName(rs.getString("employee_name"));
        return task;
    }
}

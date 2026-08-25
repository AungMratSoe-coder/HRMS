package com.ams.hrms.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.EmployeeShift;
import com.ams.hrms.model.Shift;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.EmployeeShiftRepository;
import com.ams.hrms.repository.ShiftRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Shift definitions (spec section 17) and their assignment to employees.
 * Overnight shifts are supported; assignments are effective-dated with one
 * open-ended record per employee, closed automatically when a later shift
 * begins. Every change is audited and written to the employee's history.
 */
public class ShiftService {

    public static final String DATA_SCOPE = "shifts";

    private static final Logger LOG = LoggerFactory.getLogger(ShiftService.class);

    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public ShiftService(ShiftRepository shiftRepository,
                        EmployeeShiftRepository assignmentRepository,
                        EmployeeRepository employeeRepository,
                        AuditService auditService,
                        EmployeeService employeeService) {
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    // ------------------------------------------------------------------
    // Shift CRUD
    // ------------------------------------------------------------------

    public List<Shift> findAll(String keyword) {
        SecurityService.require(Permissions.SHIFT_VIEW);
        return shiftRepository.findAll(keyword);
    }

    /** Employees currently holding this shift (Assignments tab count column). */
    public long countOpenAssignments(long shiftId) {
        SecurityService.require(Permissions.SHIFT_VIEW);
        return shiftRepository.openAssignmentCount(shiftId);
    }

    public long save(Shift shift) {
        SecurityService.require(Permissions.SHIFT_MANAGE);
        validate(shift);

        if (shift.getId() == null) {
            long id = shiftRepository.insert(shift);
            audit("CREATE", id, "Created shift '" + shift.getCode() + " - " + shift.getName() + "'");
            publishChange();
            return id;
        }
        if (shiftRepository.findById(shift.getId()).isEmpty()) {
            throw new BusinessException("Shift not found", "The shift no longer exists.");
        }
        shiftRepository.update(shift);
        audit("UPDATE", shift.getId(), "Updated shift '" + shift.getCode() + "'");
        publishChange();
        return shift.getId();
    }

    /** Soft-deletes a shift; blocked while employees are still assigned. */
    public void setStatus(long id, String status) {
        SecurityService.require(Permissions.SHIFT_MANAGE);
        Shift shift = shiftRepository.findById(id).orElseThrow(() -> new BusinessException(
                "Shift not found", "The shift no longer exists."));

        if ("INACTIVE".equals(status)) {
            long holders = shiftRepository.openAssignmentCount(id);
            if (holders > 0) {
                throw new BusinessException(
                        "Shift has " + holders + " assigned employees",
                        "This shift still has " + holders
                                + " assigned employee(s). End their assignments first.");
            }
        }
        if (!status.equals(shift.getStatus())) {
            shiftRepository.setStatus(id, status);
            audit("STATUS_CHANGE", id, "Shift '" + shift.getName() + "' set to " + status);
            publishChange();
        }
    }

    // ------------------------------------------------------------------
    // Assignments
    // ------------------------------------------------------------------

    public List<EmployeeShift> currentAssignments() {
        SecurityService.requireAny(Permissions.SHIFT_VIEW, Permissions.SHIFT_ASSIGN);
        // Plain employees see only their own assignment row.
        Long scope = employeeService.selfScopeEmployeeId();
        return assignmentRepository.findCurrent(scope);
    }

    public List<EmployeeShift> historyForEmployee(long employeeId) {
        SecurityService.requireAny(Permissions.SHIFT_VIEW, Permissions.SHIFT_ASSIGN);
        if (!employeeService.isOwnRecord(employeeId)) {
            employeeService.requireVisible(employeeId);
        }
        return assignmentRepository.findByEmployee(employeeId);
    }

    /**
     * Assigns a shift starting {@code effectiveFrom}. A previous open-ended
     * assignment is closed the day before; same-day replacement replaces the
     * row in place. A SHIFT_CHANGE history entry is written for the employee.
     */
    public void assign(long employeeId, long shiftId, LocalDate effectiveFrom) {
        SecurityService.require(Permissions.SHIFT_ASSIGN);
        if (effectiveFrom == null) {
            throw new ValidationException(List.of("Effective date is required."));
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(
                        "Employee not found", "The employee no longer exists."));
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new BusinessException(
                    "Employee is not active",
                    "Only ACTIVE employees can be assigned to a shift.");
        }
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BusinessException(
                        "Shift not found", "The selected shift does not exist."));
        if (!"ACTIVE".equals(shift.getStatus())) {
            throw new BusinessException("Shift is inactive",
                    "Inactive shifts cannot be assigned.");
        }
        var previous = assignmentRepository.findOpenForEmployee(employeeId);
        boolean replacingExactStart =
                assignmentRepository.hasAssignmentStartingAt(employeeId, effectiveFrom);
        if (!replacingExactStart && previous.isEmpty()
                && assignmentRepository.overlaps(employeeId, effectiveFrom, null, null)) {
            throw new ValidationException(List.of(
                    "This date overlaps an existing assignment. End that one first or pick a later start date."));
        }
        if (previous.isEmpty()
                && assignmentRepository.hasFutureAssignment(employeeId)
                && !replacingExactStart) {
            throw new ValidationException(List.of(
                    "A future shift change is already scheduled for this employee. End it first."));
        }
        // The replaced row (if any) supplies the "from" side of the history entry.
        String oldName = assignmentRepository
                .shiftNameStartingAt(employeeId, effectiveFrom)
                .or(() -> previous.map(EmployeeShift::getShiftName))
                .orElse(null);

        TransactionManager.execute(tx -> {
            // Remove an existing row starting exactly on the new date
            // (same-day amendment), then close every other assignment still
            // active the day before the new shift begins.
            tx.executeUpdate(
                    "DELETE FROM employee_shifts WHERE employee_id = ? AND effective_from = ?",
                    employeeId, effectiveFrom);
            tx.executeUpdate(
                    "UPDATE employee_shifts SET effective_to = ? "
                            + "WHERE employee_id = ? AND effective_from < ? "
                            + "AND (effective_to IS NULL OR effective_to >= ?)",
                    effectiveFrom.minusDays(1), employeeId, effectiveFrom, effectiveFrom);

            var toInsert = new EmployeeShift();
            toInsert.setEmployeeId(employeeId);
            toInsert.setShiftId(shiftId);
            toInsert.setEffectiveFrom(effectiveFrom);
            assignmentRepository.insert(tx, toInsert);
            return null;
        });

        auditService.record("ASSIGN", DATA_SCOPE.toUpperCase(), "Employee", employeeId,
                "Assigned shift '" + shift.getName() + "' to "
                        + employee.getCode() + " from " + effectiveFrom);
        writeHistory(employeeId, oldName, shift.getName(), effectiveFrom);
        LOG.info("Assigned shift '{}' to employee {}", shift.getName(), employee.getCode());
        publishChange();
    }

    /** Ends an open-ended assignment as of {@code endDate} (inclusive). */
    public void endAssignment(long assignmentId, LocalDate endDate) {
        SecurityService.require(Permissions.SHIFT_ASSIGN);
        if (endDate == null) {
            throw new ValidationException(List.of("End date is required."));
        }
        List<EmployeeShift> current = assignmentRepository.findCurrent();
        EmployeeShift target = current.stream()
                .filter(a -> assignmentId == a.getId()).findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Assignment not found", "The assignment was already ended."));
        if (endDate.isBefore(target.getEffectiveFrom())) {
            throw new ValidationException(List.of(
                    "End date cannot be before the assignment start (" + target.getEffectiveFrom() + ")."));
        }
        assignmentRepository.endAssignment(assignmentId, endDate);
        auditService.record("STATUS_CHANGE", DATA_SCOPE.toUpperCase(), "EmployeeShift",
                assignmentId, "Ended assignment of '" + target.getShiftName() + "' on " + endDate);
        writeHistory(target.getEmployeeId(), target.getShiftName(), "(none)", endDate);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validate(Shift shift) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, shift.getCode(), "Shift code");
        Validators.pattern(errors, shift.getCode(), "Shift code", Validators.CODE_PATTERN, "SH-NIGHT");
        Validators.required(errors, shift.getName(), "Shift name");
        Validators.maxLength(errors, shift.getDescription(), 255, "Description");

        java.time.LocalTime start = Validators.parseTime(errors,
                shift.getStartTime() == null ? "" : shift.getStartTime().toString(), "Start time");
        java.time.LocalTime end = Validators.parseTime(errors,
                shift.getEndTime() == null ? "" : shift.getEndTime().toString(), "End time");
        if (start != null && end != null && start.equals(end)) {
            errors.add("Start and end times cannot be identical.");
        }
        if (shift.getGraceMinutes() < 0 || shift.getGraceMinutes() > 240) {
            errors.add("Grace period must be between 0 and 240 minutes.");
        }
        if (shift.getBreakMinutes() < 0 || shift.getBreakMinutes() > 480) {
            errors.add("Break time must be between 0 and 480 minutes.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        shift.setCode(Validators.normalize(shift.getCode()).toUpperCase());
        shift.setName(Validators.normalize(shift.getName()));

        if (shiftRepository.codeExists(shift.getCode(), shift.getId())) {
            throw new ValidationException(List.of(
                    "Shift code '" + shift.getCode() + "' is already in use."));
        }
        if (shiftRepository.nameExists(shift.getName(), shift.getId())) {
            throw new ValidationException(List.of(
                    "Shift name '" + shift.getName() + "' is already in use."));
        }
    }

    private void writeHistory(long employeeId, String oldName, String newName, LocalDate date) {
        new com.ams.hrms.repository.Sql().executeUpdate(
                "INSERT INTO employee_history (employee_id, change_type, effective_date, old_value, "
                        + "new_value, remarks, recorded_by) VALUES (?, 'SHIFT_CHANGE', ?, ?, ?, ?, ?)",
                employeeId, date, oldName, newName, "Shift assignment",
                com.ams.hrms.security.SessionContext.currentUserId());
    }

    private void audit(String action, Long entityId, String description) {
        auditService.record(action, "SHIFT", "Shift", entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

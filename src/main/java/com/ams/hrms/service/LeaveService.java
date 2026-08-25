package com.ams.hrms.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeLeaveRequest;
import com.ams.hrms.repository.LeaveRepository;
import com.ams.hrms.repository.LeaveRepository.BalanceRow;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;

/**
 * Leave management (spec section 18): requests with overlap protection
 * (spec section 46 rule 5), balance ledger (rule 4: never exceed available),
 * approval workflow with recorded levels, cancellation and RBAC.
 *
 * <p>Balance ledger per request: PENDING adds to {@code pending}; approval
 * moves days from pending to used; rejection/cancellation releases pending.</p>
 */
public class LeaveService {

    public static final String DATA_SCOPE = "leave";

    private static final Logger LOG = LoggerFactory.getLogger(LeaveService.class);

    private final LeaveRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public LeaveService(LeaveRepository repository, AuditService auditService,
                        EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Listing; plain EMPLOYEE accounts only ever see their own requests. */
    public List<EmployeeLeaveRequest> findAll(String keyword, String status, Long typeId) {
        SecurityService.require(Permissions.LEAVE_VIEW);
        return repository.findAll(keyword, status, typeId,
                employeeService.selfScopeEmployeeId());
    }

    /** All leave requests of one employee, newest first (profile view). */
    public List<EmployeeLeaveRequest> findForEmployee(long employeeId) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.LEAVE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.findByEmployee(employeeId);
    }

    public List<LeaveRepository.LeaveTypeOption> activeTypes() {
        SecurityService.require(Permissions.LEAVE_VIEW);
        return repository.findActiveTypes();
    }

    public List<BalanceRow> balances(long employeeId, int year) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.LEAVE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.findBalances(employeeId, year);
    }

    /** Available days for one type/year; auto-provisions the balance row. */
    public BigDecimal availableDays(long employeeId, long leaveTypeId, int year) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.LEAVE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        long balanceId = repository.ensureBalance(employeeId, leaveTypeId, year);
        return repository.findBalances(employeeId, year).stream()
                .filter(row -> row.id() == balanceId)
                .findFirst()
                .map(BalanceRow::available)
                .orElse(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Request lifecycle
    // ------------------------------------------------------------------

    /** Submits a new request (requester = current user's employee record). */
    public long request(EmployeeLeaveRequest request) {
        SecurityService.require(Permissions.LEAVE_REQUEST);
        // Plain employees always file for themselves, whatever the UI sent.
        Long scope = employeeService.selfScopeEmployeeId();
        if (scope != null) {
            request.setEmployeeId(scope);
        }
        validateDates(request);
        validateOverlap(request);
        ensureBalanceAndCapacity(request);

        TransactionManager.execute(tx -> {
            long id = repository.insertRequest(request);
            String code = "LR-" + String.format("%04d", id);
            repository.updateLeaveCode(id, code);
            repository.adjustPending(request.getEmployeeId(), request.getLeaveTypeId(),
                    request.getStartDate().getYear(), request.getNumberOfDays());
            request.setId(id);
            request.setLeaveCode(code);
            return null;
        });

        audit("REQUEST", request.getId(),
                "Leave " + request.getLeaveCode() + " requested by employee #"
                        + request.getEmployeeId() + " (" + request.getNumberOfDays() + " day(s))");
        publishChange();
        EventBus.publish(new Events.LeaveRequested(request.getId(), request.getLeaveCode(),
                request.getEmployeeId(), SessionContext.currentUser().fullName()));
        return request.getId();
    }

    /**
     * Approves at the given level. HR-level approval is final: the status
     * flips to APPROVED and days move from pending to used.
     */
    public void approve(long requestId, String level, String comments) {
        SecurityService.require(Permissions.LEAVE_APPROVE);
        EmployeeLeaveRequest request = requirePending(requestId);
        employeeService.requireMayDecideFor(request.getEmployeeId(),
                "HR".equalsIgnoreCase(level));
        long approverId = SessionContext.currentUserId();

        if ("HR".equalsIgnoreCase(level)) {
            TransactionManager.execute(tx -> {
                repository.approveRequest(requestId, approverId);
                repository.insertApproval(requestId, approverId, "HR", "APPROVED", comments);
                repository.approveUsage(request.getEmployeeId(), request.getLeaveTypeId(),
                        request.getStartDate().getYear(), request.getNumberOfDays());
                return null;
            });
            audit("APPROVE", requestId, "Leave " + request.getLeaveCode() + " approved (final)");
            EventBus.publish(new Events.LeaveDecided(requestId, request.getLeaveCode(),
                    request.getEmployeeId(), true, SessionContext.currentUser().fullName()));
        } else {
            repository.insertApproval(requestId, approverId, "MANAGER", "APPROVED", comments);
            audit("APPROVE", requestId, "Leave " + request.getLeaveCode()
                    + " approved at manager level");
        }
        publishChange();
    }

    public void reject(long requestId, String reason) {
        SecurityService.require(Permissions.LEAVE_APPROVE);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(List.of("A rejection reason is required."));
        }
        EmployeeLeaveRequest request = requirePending(requestId);
        employeeService.requireMayDecideFor(request.getEmployeeId(), false);
        long approverId = SessionContext.currentUserId();
        TransactionManager.execute(tx -> {
            repository.rejectRequest(requestId, approverId, reason.trim());
            repository.insertApproval(requestId, approverId, "HR", "REJECTED", reason.trim());
            repository.adjustPending(request.getEmployeeId(), request.getLeaveTypeId(),
                    request.getStartDate().getYear(), request.getNumberOfDays().negate());
            return null;
        });
        audit("REJECT", requestId, "Leave " + request.getLeaveCode() + " rejected");
        EventBus.publish(new Events.LeaveDecided(requestId, request.getLeaveCode(),
                request.getEmployeeId(), false, SessionContext.currentUser().fullName()));
        publishChange();
    }

    /** Cancels a pending request: the requester or any LEAVE_CANCEL holder. */
    public void cancel(long requestId) {
        EmployeeLeaveRequest request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException(
                        "Request not found", "The leave request no longer exists."));

        boolean isOwner = SessionContext.isAuthenticated()
                && SessionContext.currentUserId() != null
                && isOwnRequest(request);
        if (isOwner) {
            SecurityService.require(Permissions.LEAVE_REQUEST);
        } else {
            SecurityService.require(Permissions.LEAVE_CANCEL);
        }
        requirePending(requestId);

        TransactionManager.execute(tx -> {
            repository.cancelRequest(requestId);
            repository.adjustPending(request.getEmployeeId(), request.getLeaveTypeId(),
                    request.getStartDate().getYear(), request.getNumberOfDays().negate());
            return null;
        });
        audit("CANCEL", requestId, "Leave " + request.getLeaveCode() + " cancelled");
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validateDates(EmployeeLeaveRequest request) {
        List<String> errors = LeaveRules.validateRequest(
                request.getReason(), request.getStartDate(), request.getEndDate());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        request.setNumberOfDays(BigDecimal.valueOf(
                LeaveRules.daysInclusive(request.getStartDate(), request.getEndDate())));
        if (request.getLeaveTypeId() <= 0) {
            throw new ValidationException(List.of("Leave type is required."));
        }
    }

    private void validateOverlap(EmployeeLeaveRequest request) {
        if (repository.overlaps(request.getEmployeeId(), request.getStartDate(),
                request.getEndDate(), null)) {
            throw new ValidationException(List.of(
                    "These dates overlap an existing pending or approved leave."));
        }
    }

    /** Balance rule 4: requested days must fit the remaining availability. */
    private void ensureBalanceAndCapacity(EmployeeLeaveRequest request) {
        int year = request.getStartDate().getYear();
        var types = repository.findActiveTypes().stream()
                .filter(type -> type.id() == request.getLeaveTypeId())
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        List.of("Leave type is invalid.")));
        if (types.genderRestriction() != null) {
            String gender = new Sql().first(
                    "SELECT gender FROM employees WHERE id = ?",
                    rs -> rs.getString("gender"), request.getEmployeeId()).orElse("");
            if (!gender.isBlank() && !gender.equals(types.genderRestriction())) {
                throw new ValidationException(List.of(
                        types.name() + " is only available to " + types.genderRestriction()
                                + " employees."));
            }
        }

        long balanceId = repository.ensureBalance(request.getEmployeeId(),
                request.getLeaveTypeId(), year);
        BalanceRow row = repository.findBalances(request.getEmployeeId(), year).stream()
                .filter(bal -> bal.id() == balanceId)
                .findFirst()
                .orElseThrow();
        if (!LeaveRules.hasSufficientBalance(row.available(), request.getNumberOfDays())) {
            throw new ValidationException(List.of(LeaveRules.insufficientBalanceMessage(
                    row.available(), request.getNumberOfDays(), types.name())));
        }
    }

    private EmployeeLeaveRequest requirePending(long requestId) {
        EmployeeLeaveRequest request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException(
                        "Request not found", "The leave request no longer exists."));
        if (!request.isPending()) {
            throw new BusinessException(
                    "Request already decided",
                    "Only PENDING leave requests can be actioned.");
        }
        return request;
    }

    /** Owner check compares the request's employee with the session user's link. */
    private boolean isOwnRequest(EmployeeLeaveRequest request) {
        Long linked = SessionContext.currentEmployeeId();
        if (linked == null) {
            String email = SessionContext.currentUser().email();
            linked = email == null || email.isBlank() ? null
                    : new Sql().first(
                            "SELECT id FROM employees WHERE email = ?",
                            rs -> rs.getLong("id"), email).orElse(null);
        }
        return linked != null && linked == request.getEmployeeId();
    }

    private void audit(String action, Long entityId, String description) {
        auditService.record(action, "LEAVE", "LeaveRequest", entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

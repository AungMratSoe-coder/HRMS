package com.ams.hrms.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.OvertimeRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Overtime requests (spec section 19). Arithmetic lives in the pure,
 * unit-tested {@link OvertimeRules}; this service resolves configuration
 * from app_settings, snapshots the rate at approval time and audits every
 * decision.
 */
public class OvertimeService {

    public static final String DATA_SCOPE = "overtime";

    private static final Logger LOG = LoggerFactory.getLogger(OvertimeService.class);

    private final OvertimeRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public OvertimeService(OvertimeRepository repository, AuditService auditService,
                           EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    /** Listing; plain EMPLOYEE accounts only ever see their own requests. */
    public List<OvertimeRequest> findAll(String keyword, String status) {
        SecurityService.require(Permissions.OVERTIME_VIEW);
        return repository.findAll(keyword, status,
                employeeService.selfScopeEmployeeId());
    }

    /** Submits a request; the rate is resolved when it is approved. */
    public long request(OvertimeRequest request) {
        SecurityService.require(Permissions.OVERTIME_REQUEST);
        // Plain employees always file for themselves, whatever the UI sent.
        Long scope = employeeService.selfScopeEmployeeId();
        if (scope != null) {
            request.setEmployeeId(scope);
        }

        List<String> errors = OvertimeRules.validateRequest(
                request.getRequestDate(), request.getHours(), request.getReason());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        TransactionManager.execute(tx -> {
            long id = repository.insert(request);
            String code = "OT-" + String.format("%04d", id);
            repository.updateOvertimeCode(id, code);
            request.setId(id);
            request.setOvertimeCode(code);
            return null;
        });
        audit("REQUEST", request.getId(), "Overtime " + request.getOvertimeCode()
                + " requested by employee #" + request.getEmployeeId()
                + " (" + request.getHours().toPlainString() + "h on "
                + request.getRequestDate() + ")");
        publishChange();
        return request.getId();
    }

    /**
     * Approves: resolves hourly base from the employee's CURRENT basic salary,
     * snapshots the rate and computes the amount.
     */
    public void approve(long requestId) {
        SecurityService.require(Permissions.OVERTIME_APPROVE);
        OvertimeRequest request = requirePending(requestId);

        OvertimeRules.RateBreakdown breakdown = resolveRate(request.getEmployeeId());
        BigDecimal amount = OvertimeRules.amount(request.getHours(), breakdown.ratePerHour());
        long approverId = com.ams.hrms.security.SessionContext.currentUserId();

        repository.approve(requestId, breakdown.ratePerHour(), amount, approverId);
        audit("APPROVE", requestId, "Overtime " + request.getOvertimeCode() + " approved: "
                + request.getHours().toPlainString() + "h @ "
                + breakdown.ratePerHour().toPlainString() + "/h = " + amount.toPlainString());
        publishChange();
    }

    public void reject(long requestId) {
        SecurityService.require(Permissions.OVERTIME_APPROVE);
        OvertimeRequest request = requirePending(requestId);
        long approverId = com.ams.hrms.security.SessionContext.currentUserId();
        repository.reject(requestId, approverId);
        audit("REJECT", requestId, "Overtime " + request.getOvertimeCode() + " rejected");
        publishChange();
    }

    /** Exposed for the payslip module (Phase 14/15): current rule snapshot. */
    public OvertimeRules.RateBreakdown currentRate(BigDecimal basicSalary) {
        return computeRate(basicSalary);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private OvertimeRules.RateBreakdown resolveRate(long employeeId) {
        BigDecimal basicSalary = new Sql().first(
                "SELECT basic_salary FROM employees WHERE id = ?",
                rs -> rs.getBigDecimal("basic_salary"), employeeId)
                .orElse(BigDecimal.ZERO);
        return computeRate(basicSalary);
    }

    private OvertimeRules.RateBreakdown computeRate(BigDecimal basicSalary) {
        BigDecimal workingDays = settingDecimal("payroll.working_days_per_month",
                OvertimeRules.DEFAULT_WORKING_DAYS);
        BigDecimal multiplier = settingDecimal("payroll.overtime_rate_multiplier",
                OvertimeRules.DEFAULT_MULTIPLIER);
        return OvertimeRules.rate(basicSalary, workingDays, multiplier);
    }

    private BigDecimal settingDecimal(String key, BigDecimal fallback) {
        try {
            String raw = new Sql().first(
                    "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                    rs -> rs.getString(1), key).orElse("");
            if (raw.isBlank()) {
                return fallback;
            }
            return new BigDecimal(raw.trim());
        } catch (RuntimeException e) {
            LOG.warn("Setting '{}' unreadable; using fallback {}", key, fallback);
            return fallback;
        }
    }

    private OvertimeRequest requirePending(long requestId) {
        OvertimeRequest request = repository.findById(requestId)
                .orElseThrow(() -> new BusinessException(
                        "Request not found", "The overtime request no longer exists."));
        if (!request.isPending()) {
            throw new BusinessException(
                    "Request already decided",
                    "Only PENDING overtime requests can be actioned.");
        }
        return request;
    }

    private void audit(String action, Long entityId, String description) {
        auditService.record(action, DATA_SCOPE.toUpperCase(), "OvertimeRequest",
                entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

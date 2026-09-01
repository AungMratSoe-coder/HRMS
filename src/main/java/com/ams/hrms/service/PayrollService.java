package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.repository.PayrollRepository;
import com.ams.hrms.repository.PayrollRepository.EmployeePayrollData;
import com.ams.hrms.repository.PayrollRepository.PayrollRow;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Payroll orchestration (spec section 20): calculate period, then state
 * machine transitions REVIEWED → APPROVED → PAID, each RBAC-gated and
 * audited. Rule 6: one payroll per employee/period (unique key + check).
 * Rule 7: approved payrolls are immutable.
 */
public class PayrollService {

    public static final String DATA_SCOPE = "payroll";

    private static final Logger LOG = LoggerFactory.getLogger(PayrollService.class);

    private final PayrollRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public PayrollService(PayrollRepository repository, AuditService auditService,
                          EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    public List<PayrollRepository.Period> allPeriods() {
        SecurityService.require(Permissions.PAYROLL_VIEW);
        return repository.allPeriods();
    }

    public List<PayrollRow> findByPeriod(long periodId) {
        SecurityService.require(Permissions.PAYROLL_VIEW);
        return repository.findByPeriod(periodId);
    }

    /**
     * All payroll rows of one employee; payslip viewers may read them too.
     * PAYSLIP_VIEW holders without directory rights are limited to their
     * own rows.
     */
    public List<PayrollRow> findByEmployee(long employeeId) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.requireAny(Permissions.PAYROLL_VIEW, Permissions.PAYSLIP_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.findByEmployee(employeeId);
    }

    /**
     * Calculates payroll for all active employees in a period. Existing rows
     * for that period are skipped (rule 6). The whole run is one transaction:
     * a failure mid-loop rolls back every inserted row so a period can never
     * end up half-calculated. Returns how many were calculated.
     */
    public int calculate(int year, int month) {
        SecurityService.require(Permissions.PAYROLL_CALCULATE);

        long periodId = repository.findOrCreatePeriod(year, month);
        var period = repository.allPeriods().stream()
                .filter(p -> p.id() == periodId).findFirst().orElseThrow();
        LocalDate from = period.startDate();
        LocalDate to = period.endDate();

        BigDecimal taxRate = repository.settingDecimal("payroll.tax_rate_percent",
                new BigDecimal("5"));
        BigDecimal ssRate = repository.settingDecimal(
                "payroll.social_security_employee_percent", new BigDecimal("2"));
        String currency = repository.currency();

        List<EmployeePayrollData> employees = repository.activeEmployeesForPayroll();
        long userId = com.ams.hrms.security.SessionContext.currentUserId();

        int calculated = TransactionManager.execute(tx -> {
            int count = 0;
            for (EmployeePayrollData employee : employees) {
                if (repository.existsForEmployeePeriod(tx, employee.employeeId(), periodId)) {
                    continue; // rule 6: skip duplicates
                }
                BigDecimal allowances = repository.allowanceTotal(tx, employee.employeeId(), from, to);
                BigDecimal bonuses = repository.bonusTotal(tx, employee.employeeId(), from, to);
                BigDecimal overtime = repository.overtimeTotal(tx, employee.employeeId(), from, to);
                BigDecimal otherDeduction = repository.otherDeductionTotal(
                        tx, employee.employeeId(), from, to);

                BigDecimal gross = PayrollCalculator.gross(
                        employee.basicSalary(), allowances, bonuses, overtime);
                BigDecimal tax = PayrollCalculator.tax(gross, taxRate);
                BigDecimal ss = PayrollCalculator.socialSecurity(gross, ssRate);
                BigDecimal totalDed = PayrollCalculator.totalDeduction(tax, ss, otherDeduction);
                BigDecimal net = PayrollCalculator.net(gross, totalDed);

                String number = String.format("PR-%d-%02d-%s", year, month, employee.code());
                repository.insertPayroll(tx, employee.employeeId(), periodId, number, currency,
                        employee.basicSalary(), gross, tax, ss, otherDeduction,
                        totalDed, net, userId);
                count++;
            }
            return count;
        });
        auditService.record("CALCULATE", DATA_SCOPE.toUpperCase(), "PayrollPeriod",
                periodId, "Calculated " + calculated + " payroll record(s) for "
                        + String.format("%d-%02d", year, month));
        publishChange();
        EventBus.publish(new Events.PayrollProcessed("CALCULATED",
                String.format("%d-%02d", year, month), calculated));
        LOG.info("Payroll calculated: {} records for {}-{:02}", calculated, year, month);
        return calculated;
    }

    /**
     * Moves one payroll record through the state machine
     * ({@code CALCULATED → REVIEWED → APPROVED → PAID}, PayrollRules). The
     * guarded update re-checks the current status at write time, so two users
     * transitioning the same record can never both succeed.
     */
    public void transition(long payrollId, String targetStatus) {
        Permissions permission = switch (targetStatus) {
            case "REVIEWED" -> Permissions.PAYROLL_REVIEW;
            case "APPROVED" -> Permissions.PAYROLL_APPROVE;
            case "PAID" -> Permissions.PAYROLL_MARK_PAID;
            default -> throw new BusinessException(
                    "Invalid status: " + targetStatus,
                    "Unknown payroll transition.");
        };
        SecurityService.require(permission);
        String requiredSource = requireLegalTarget(targetStatus);

        boolean moved = TransactionManager.execute(tx -> repository.transition(
                tx, payrollId, requiredSource, targetStatus,
                com.ams.hrms.security.SessionContext.currentUserId()));
        if (!moved) {
            throw transitionFailure(payrollId, targetStatus);
        }
        auditService.record(targetStatus, DATA_SCOPE.toUpperCase(), "Payroll",
                payrollId, "Payroll #" + payrollId + " moved to " + targetStatus);
        publishChange();
    }

    /**
     * Bulk-transitions all records of a period that are currently in
     * {@code fromStatus}. {@code fromStatus} must be the state machine's
     * required source of {@code toStatus}; each row's guard is re-checked at
     * write time and any concurrent change aborts the whole batch atomically.
     */
    public void transitionPeriod(long periodId, String fromStatus, String toStatus) {
        Permissions permission = switch (toStatus) {
            case "REVIEWED" -> Permissions.PAYROLL_REVIEW;
            case "APPROVED" -> Permissions.PAYROLL_APPROVE;
            case "PAID" -> Permissions.PAYROLL_MARK_PAID;
            default -> throw new BusinessException(
                    "Invalid status: " + toStatus, "Unknown payroll transition.");
        };
        SecurityService.require(permission);
        String requiredSource = requireLegalTarget(toStatus);
        if (!requiredSource.equals(fromStatus)) {
            throw new BusinessException(
                    "Illegal bulk transition " + fromStatus + " -> " + toStatus,
                    "Records cannot move from '" + fromStatus + "' to '" + toStatus
                            + "'. Only '" + requiredSource + "' can become '" + toStatus + "'.");
        }

        int movedCount = TransactionManager.execute(tx -> {
            List<PayrollRow> rows = repository.findByPeriod(tx, periodId).stream()
                    .filter(row -> row.status().equals(fromStatus))
                    .toList();
            long userId = com.ams.hrms.security.SessionContext.currentUserId();
            for (var row : rows) {
                if (!repository.transition(tx, row.id(), fromStatus, toStatus, userId)) {
                    throw new BusinessException(
                            "Payroll record " + row.payrollNumber()
                                    + " changed concurrently during bulk transition",
                            "'" + row.payrollNumber() + "' is no longer '" + fromStatus
                                    + "'. Refresh the list and try again.");
                }
            }
            if (toStatus.equals("APPROVED")) {
                repository.lockPeriod(tx, periodId);
            }
            return rows.size();
        });
        auditService.record(toStatus, DATA_SCOPE.toUpperCase(), "PayrollPeriod",
                periodId, "Bulk transitioned " + movedCount
                        + " record(s) from " + fromStatus + " to " + toStatus);
        publishChange();
        if (toStatus.equals("PAID")) {
            String periodLabel = repository.allPeriods().stream()
                    .filter(p -> p.id() == periodId).findFirst()
                    .map(PayrollRepository.Period::periodName).orElse(String.valueOf(periodId));
            EventBus.publish(new Events.PayrollProcessed("PAID", periodLabel, movedCount));
        }
    }

    /** Rejects unknown targets and entry-only statuses up front. */
    private String requireLegalTarget(String targetStatus) {
        String requiredSource = PayrollRules.requiredSourceOf(targetStatus);
        if (requiredSource == null) {
            throw new BusinessException(
                    "No legal source status reaches '" + targetStatus + "'",
                    "'" + targetStatus + "' is not a status payroll records can be"
                            + " moved to.");
        }
        return requiredSource;
    }

    /** Turns a failed guard into the most helpful error possible. */
    private BusinessException transitionFailure(long payrollId, String targetStatus) {
        String current = repository.findStatusById(payrollId)
                .orElseThrow(() -> new BusinessException(
                        "Payroll record #" + payrollId + " not found",
                        "This payroll record no longer exists."));
        return new BusinessException(
                "Illegal payroll transition to " + targetStatus + "; current status is "
                        + current,
                "This record is now '" + current + "', so it cannot be moved to '"
                        + targetStatus + "'. Refresh the list.");
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

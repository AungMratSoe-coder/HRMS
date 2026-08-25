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
import com.ams.hrms.model.Resignation;
import com.ams.hrms.model.Termination;
import com.ams.hrms.repository.AssetRepository;
import com.ams.hrms.repository.EmployeeShiftRepository;
import com.ams.hrms.repository.SeparationRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Employee separation (spec section 26): resignations flow SUBMITTED &rarr;
 * APPROVED &rarr; PROCESSED (or REJECTED / WITHDRAWN); terminations are
 * effective immediately on record. Processing runs the exit checklist in one
 * transaction:
 * <ol>
 *   <li>employee status &rarr; RESIGNED / TERMINATED (history preserved)</li>
 *   <li>open shift assignment closed</li>
 *   <li>open asset assignments returned, assets released</li>
 *   <li>DRAFT/CALCULATED payroll rows voided (APPROVED+ stays immutable)</li>
 * </ol>
 * Nothing is ever physically deleted.
 */
public class SeparationService {

    public static final String DATA_SCOPE = "separation";

    private static final Logger LOG = LoggerFactory.getLogger(SeparationService.class);

    private final SeparationRepository repository;
    private final EmployeeShiftRepository shiftRepository;
    private final AssetRepository assetRepository;
    private final AuditService auditService;
    private final com.ams.hrms.repository.EmployeeRepository employeeRepository;

    public SeparationService(SeparationRepository repository,
                             EmployeeShiftRepository shiftRepository,
                             AssetRepository assetRepository,
                             AuditService auditService,
                             com.ams.hrms.repository.EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.shiftRepository = shiftRepository;
        this.assetRepository = assetRepository;
        this.auditService = auditService;
        this.employeeRepository = employeeRepository;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<Resignation> findResignations(String keyword, String status) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        return repository.findResignations(keyword, status);
    }

    public List<Termination> findTerminations(String keyword) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        return repository.findTerminations(keyword);
    }

    public Resignation findResignationById(long id) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        return requireResignation(id);
    }

    // ------------------------------------------------------------------
    // Resignations
    // ------------------------------------------------------------------

    /** Records a resignation for an ACTIVE employee; returns the new id. */
    public long recordResignation(Resignation resignation) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        validateResignation(resignation);

        if (!repository.isActiveEmployee(resignation.getEmployeeId())) {
            throw new ValidationException(List.of(
                    "Resignations can only be recorded for ACTIVE employees."));
        }
        if (repository.hasOpenResignation(resignation.getEmployeeId())) {
            throw new ValidationException(List.of(
                    "This employee already has an open resignation."));
        }

        long id = repository.insertResignation(resignation);
        String code = "RES-" + String.format("%04d", id);
        repository.updateResignationCode(id, code);
        audit("CREATE", "Resignation", id,
                "Recorded resignation " + code + " for "
                        + resignation.getEmployeeCode() + ", last working day "
                        + resignation.getLastWorkingDate() + " (notice "
                        + resignation.getNoticePeriodDays() + " day(s))");
        publishChange();
        return id;
    }

    /** SUBMITTED -&gt; APPROVED. */
    public void approveResignation(long resignationId) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        Resignation resignation = requireResignation(resignationId);
        transitionResignation(resignation, Resignation.STATUS_APPROVED);
        repository.updateResignationStatus(resignationId, Resignation.STATUS_APPROVED);
        audit("STATUS_CHANGE", "Resignation", resignationId,
                resignation.getResignationCode() + " approved");
        publishChange();
    }

    /** SUBMITTED -&gt; REJECTED / WITHDRAWN. */
    public void closeResignation(long resignationId, String targetStatus) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        if (!Resignation.STATUS_REJECTED.equals(targetStatus)
                && !Resignation.STATUS_WITHDRAWN.equals(targetStatus)) {
            throw new ValidationException(List.of(
                    "A resignation can only be rejected or withdrawn here."));
        }
        Resignation resignation = requireResignation(resignationId);
        transitionResignation(resignation, targetStatus);
        repository.updateResignationStatus(resignationId, targetStatus);
        audit("STATUS_CHANGE", "Resignation", resignationId,
                resignation.getResignationCode() + " set to " + targetStatus);
        publishChange();
    }

    /**
     * APPROVED -&gt; PROCESSED: runs the transactional exit checklist
     * (status change, shift close, asset return, payroll void).
     *
     * @return human-readable summary of what the checklist did
     */
    public String processResignation(long resignationId) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        Resignation resignation = requireResignation(resignationId);
        transitionResignation(resignation, Resignation.STATUS_PROCESSED);

        LocalDate effectiveDate = resignation.getLastWorkingDate();
        ExitSummary summary = runExitChecklist(resignation.getEmployeeId(),
                effectiveDate, SeparationRules.EMPLOYEE_RESIGNED);
        repository.updateResignationStatus(resignationId, Resignation.STATUS_PROCESSED);

        audit("PROCESS", "Resignation", resignationId,
                resignation.getResignationCode() + " processed: " + summary.describe());
        LOG.info("Resignation {} processed for {}", resignation.getResignationCode(),
                resignation.getEmployeeCode());
        publishChange();
        return summary.describe();
    }

    /** Records exit interview notes on an APPROVED/PROCESSED resignation. */
    public void saveExitInterviewNotes(long resignationId, String notes) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        List<String> errors = new ArrayList<>();
        Validators.maxLength(errors, notes, 2000, "Exit interview notes");
        Validators.required(errors, notes, "Exit interview notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Resignation resignation = requireResignation(resignationId);
        if (!Resignation.STATUS_PROCESSED.equals(resignation.getStatus())
                && !Resignation.STATUS_APPROVED.equals(resignation.getStatus())) {
            throw new BusinessException("Not ready for an exit interview",
                    "Exit interview notes are recorded after approval.");
        }
        repository.updateExitInterviewNotes(resignationId, Validators.normalize(notes));
        audit("UPDATE", "Resignation", resignationId,
                "Exit interview notes recorded on " + resignation.getResignationCode());
        publishChange();
    }

    // ------------------------------------------------------------------
    // Terminations
    // ------------------------------------------------------------------

    /**
     * Records a termination and executes the exit checklist in one go -
     * terminations are effective immediately. Returns the checklist summary.
     */
    public String terminate(Termination termination) {
        SecurityService.require(Permissions.SEPARATION_MANAGE);
        validateTermination(termination);

        if (!repository.isActiveEmployee(termination.getEmployeeId())) {
            throw new ValidationException(List.of(
                    "Terminations can only be recorded for ACTIVE employees."));
        }

        long id = TransactionManager.execute(tx -> {
            long created = repository.insertTermination(termination);
            repository.updateTerminationCode(created,
                    "TERM-" + String.format("%04d", created));
            termination.setId(created);
            termination.setTerminationCode("TERM-" + String.format("%04d", created));
            runExitChecklistInternal(tx, termination.getEmployeeId(),
                    termination.getTerminationDate(), SeparationRules.EMPLOYEE_TERMINATED);
            return created;
        });

        audit("CREATE", "Termination", id,
                "Terminated " + termination.getEmployeeCode() + " ("
                        + termination.getReasonCategory() + "), code "
                        + termination.getTerminationCode());
        publishChange();
        return "Termination " + termination.getTerminationCode()
                + " recorded and exit checklist completed.";
    }

    // ------------------------------------------------------------------
    // Exit checklist
    // ------------------------------------------------------------------

    private ExitSummary runExitChecklist(long employeeId, LocalDate effectiveDate,
                                         String newStatus) {
        return TransactionManager.execute(tx ->
                runExitChecklistInternal(tx, employeeId, effectiveDate, newStatus));
    }

    /** Runs inside the caller's active transaction. */
    private ExitSummary runExitChecklistInternal(com.ams.hrms.repository.Sql tx,
                                                 long employeeId, LocalDate effectiveDate,
                                                 String newStatus) {
        ExitSummary summary = new ExitSummary();

        // Status + history pair bound to this transaction (EmployeeRepository's
        // public setStatus opens its own transaction, which would nest here).
        employeeRepository.setStatusWithinTransaction(tx, employeeId, newStatus);
        auditService.record("STATUS_CHANGE", "EMPLOYEE", "Employee", employeeId,
                "Employee #" + employeeId + " set to " + newStatus + " via separation");
        summary.statusChanged = true;

        if (shiftRepository.findOpenForEmployee(employeeId).isPresent()) {
            shiftRepository.closeOpen(tx, employeeId, effectiveDate);
            summary.shiftClosed = true;
        }

        summary.assetsReturned = assetRepository.closeOpenForEmployee(
                tx, employeeId, effectiveDate);

        summary.payrollsCancelled = repository.cancelDraftPayrolls(tx, employeeId);
        return summary;
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validateResignation(Resignation resignation) {
        List<String> errors = new ArrayList<>();
        if (resignation.getEmployeeId() <= 0) {
            errors.add("Employee is required.");
        }
        if (resignation.getResignationDate() == null) {
            errors.add("Resignation date is required.");
        }
        if (resignation.getLastWorkingDate() == null) {
            errors.add("Last working date is required.");
        }
        long noticeDays = SeparationRules.noticeDays(
                resignation.getResignationDate(), resignation.getLastWorkingDate());
        if (noticeDays < 0) {
            errors.add("Last working date cannot be before the resignation date.");
        } else {
            resignation.setNoticePeriodDays((int) noticeDays);
        }
        Validators.maxLength(errors, resignation.getReason(), 2000, "Reason");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        resignation.setReason(Validators.normalize(resignation.getReason()));
    }

    private void validateTermination(Termination termination) {
        List<String> errors = new ArrayList<>();
        if (termination.getEmployeeId() <= 0) {
            errors.add("Employee is required.");
        }
        if (termination.getTerminationDate() == null) {
            errors.add("Termination date is required.");
        }
        if (!SeparationRules.isValidCategory(termination.getReasonCategory())) {
            errors.add("Reason category must be MISCONDUCT, PERFORMANCE, LAYOFF, "
                    + "CONTRACT_END or OTHER.");
        }
        Validators.maxLength(errors, termination.getReason(), 2000, "Reason");
        Validators.maxLength(errors, termination.getNotes(), 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        termination.setReason(Validators.normalize(termination.getReason()));
        termination.setNotes(Validators.normalize(termination.getNotes()));
    }

    private void transitionResignation(Resignation resignation, String targetStatus) {
        if (!SeparationRules.canTransitionResignation(resignation.getStatus(), targetStatus)) {
            throw new BusinessException("Transition not allowed",
                    "A resignation cannot move from " + resignation.getStatus()
                            + " to " + targetStatus + ".");
        }
        resignation.setStatus(targetStatus);
    }

    private Resignation requireResignation(long resignationId) {
        return repository.findResignationById(resignationId).orElseThrow(() ->
                new BusinessException("Resignation not found",
                        "The resignation no longer exists."));
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "SEPARATION", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("employees"));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }

    /** What the exit checklist actually did - shown to HR and written to audit. */
    private static final class ExitSummary {

        private boolean statusChanged;
        private boolean shiftClosed;
        private int assetsReturned;
        private int payrollsCancelled;

        String describe() {
            StringBuilder text = new StringBuilder("Employment status updated");
            if (shiftClosed) {
                text.append(", shift assignment closed");
            }
            text.append(", ").append(assetsReturned).append(" asset(s) returned");
            text.append(", ").append(payrollsCancelled)
                    .append(" draft payroll(s) voided");
            return text.toString();
        }
    }
}

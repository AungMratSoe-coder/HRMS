package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.repository.TrainingRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Training management (spec section 23): programs with capacity, sessions
 * with computed duration, per-employee enrollment (one per program) and
 * result recording that freezes on terminal outcomes. All operations are
 * RBAC-gated and audited.
 */
public class TrainingService {

    public static final String DATA_SCOPE = "training";

    private static final Logger LOG = LoggerFactory.getLogger(TrainingService.class);

    private static final Set<String> TERMINAL_RESULTS =
            Set.of(EmployeeTraining.RESULT_PASSED, EmployeeTraining.RESULT_FAILED,
                    EmployeeTraining.RESULT_NO_SHOW);

    private final TrainingRepository repository;
    private final AuditService auditService;

    public TrainingService(TrainingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<TrainingProgram> findPrograms(String keyword, String status) {
        SecurityService.require(Permissions.TRAINING_VIEW);
        return repository.findPrograms(keyword, status);
    }

    public List<TrainingProgram> livePrograms() {
        SecurityService.require(Permissions.TRAINING_VIEW);
        return repository.findLivePrograms();
    }

    public List<TrainingSession> findSessions(Long programId, String status) {
        SecurityService.require(Permissions.TRAINING_VIEW);
        return repository.findSessions(programId, status);
    }

    public List<EmployeeTraining> findEnrollments(Long programId, String result,
                                                  String keyword) {
        SecurityService.require(Permissions.TRAINING_VIEW);
        return repository.findEnrollments(programId, result, keyword);
    }

    /** All enrollments of one employee, newest first (profile view). */
    public List<EmployeeTraining> findEnrollmentsForEmployee(long employeeId) {
        SecurityService.require(Permissions.TRAINING_VIEW);
        return repository.findEnrollmentsByEmployee(employeeId);
    }

    // ------------------------------------------------------------------
    // Programs
    // ------------------------------------------------------------------

    /** Creates or updates a program; returns the persisted id. */
    public long saveProgram(TrainingProgram program) {
        boolean isNew = program.getId() == null;
        SecurityService.require(Permissions.TRAINING_MANAGE);
        validateProgram(program);

        if (isNew) {
            program.setStatus("PLANNED");
            long id = repository.insertProgram(program);
            repository.updateProgramCode(id, "TRN-" + String.format("%04d", id));
            audit("CREATE", "TrainingProgram", id,
                    "Created training program '" + program.getName() + "'");
            publishChange();
            return id;
        }
        TrainingProgram existing = requireProgram(program.getId());
        if (!TrainingRules.programAcceptsEnrollment(existing.getStatus())) {
            throw new BusinessException("Program is closed",
                    "Only PLANNED or ONGOING programs can be edited.");
        }
        repository.updateProgram(program);
        audit("UPDATE", "TrainingProgram", program.getId(),
                "Updated training program '" + program.getCode() + "'");
        publishChange();
        return program.getId();
    }

    public void setProgramStatus(long programId, String targetStatus) {
        SecurityService.require(Permissions.TRAINING_MANAGE);
        if (!TrainingRules.PROGRAM_STATUSES.contains(targetStatus)) {
            throw new ValidationException(List.of("Unknown program status."));
        }
        TrainingProgram program = requireProgram(programId);
        if (!TrainingRules.canTransitionProgram(program.getStatus(), targetStatus)) {
            throw new BusinessException("Transition not allowed",
                    "A program cannot move from " + program.getStatus()
                            + " to " + targetStatus + ".");
        }
        repository.updateProgramStatus(programId, targetStatus);
        audit("STATUS_CHANGE", "TrainingProgram", programId,
                "Program '" + program.getCode() + "' set to " + targetStatus);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    /** Creates or updates a session of a live program; duration auto-computed. */
    public long saveSession(TrainingSession session) {
        boolean isNew = session.getId() == null;
        SecurityService.require(Permissions.TRAINING_MANAGE);
        validateSession(session);

        if (isNew) {
            session.setStatus("SCHEDULED");
            long id = repository.insertSession(session);
            audit("CREATE", "TrainingSession", id,
                    "Scheduled session for '" + session.getProgramName() + "' on "
                            + session.getStartDateTime());
            publishChange();
            return id;
        }
        TrainingSession existing = repository.findSessionById(session.getId())
                .orElseThrow(() -> new BusinessException("Session not found",
                        "The training session no longer exists."));
        if (!TrainingRules.sessionAcceptsReference(existing.getStatus())) {
            throw new BusinessException("Session is decided",
                    "Only SCHEDULED or ONGOING sessions can be edited.");
        }
        repository.updateSession(session);
        audit("UPDATE", "TrainingSession", session.getId(),
                "Updated session of '" + existing.getProgramName() + "'");
        publishChange();
        return session.getId();
    }

    public void setSessionStatus(long sessionId, String targetStatus) {
        SecurityService.require(Permissions.TRAINING_MANAGE);
        if (!TrainingRules.SESSION_STATUSES.contains(targetStatus)) {
            throw new ValidationException(List.of("Unknown session status."));
        }
        TrainingSession session = repository.findSessionById(sessionId)
                .orElseThrow(() -> new BusinessException("Session not found",
                        "The training session no longer exists."));
        if (!TrainingRules.canTransitionSession(session.getStatus(), targetStatus)) {
            throw new BusinessException("Transition not allowed",
                    "A session cannot move from " + session.getStatus()
                            + " to " + targetStatus + ".");
        }
        repository.updateSessionStatus(sessionId, targetStatus);
        audit("STATUS_CHANGE", "TrainingSession", sessionId,
                "Session of '" + session.getProgramName() + "' set to " + targetStatus);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Enrollment & results
    // ------------------------------------------------------------------

    /** Enrolls an employee into a live program (optionally pinning a session). */
    public long enroll(EmployeeTraining enrollment) {
        SecurityService.require(Permissions.TRAINING_MANAGE);
        List<String> errors = new ArrayList<>();
        if (enrollment.getEmployeeId() <= 0) {
            errors.add("Employee is required.");
        }
        Validators.maxLength(errors, enrollment.getNotes(), 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        TrainingProgram program = requireProgram(enrollment.getProgramId());
        if (!TrainingRules.programAcceptsEnrollment(program.getStatus())) {
            throw new BusinessException("Program is closed",
                    "'" + program.getName() + "' is " + program.getStatus()
                            + " and accepts no new enrollments.");
        }
        if (!TrainingRules.hasRoom(program.getCapacity(),
                repository.enrollmentCount(program.getId()))) {
            throw new BusinessException("Program full",
                    "'" + program.getName() + "' has no seats left (capacity "
                            + program.getCapacity() + ").");
        }
        if (repository.isEnrolled(program.getId(), enrollment.getEmployeeId())) {
            throw new ValidationException(List.of(
                    "This employee is already enrolled in the program."));
        }
        if (enrollment.getSessionId() != null) {
            TrainingSession session = repository
                    .findSessionById(enrollment.getSessionId())
                    .orElseThrow(() -> new BusinessException("Session not found",
                            "The training session no longer exists."));
            if (session.getProgramId() != program.getId()) {
                throw new ValidationException(List.of(
                        "The chosen session belongs to a different program."));
            }
            if (!TrainingRules.sessionAcceptsReference(session.getStatus())) {
                throw new ValidationException(List.of(
                        "The chosen session is no longer schedulable."));
            }
        }

        long id = repository.insertEnrollment(enrollment);
        audit("CREATE", "EmployeeTraining", id,
                "Enrolled " + enrollment.getEmployeeCode() + " in '"
                        + program.getName() + "'");
        publishChange();
        return id;
    }

    /** Removes an enrollment that never progressed past ENROLLED. */
    public void unenroll(long enrollmentId) {
        SecurityService.require(Permissions.TRAINING_MANAGE);
        EmployeeTraining enrollment = repository.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new BusinessException("Enrollment not found",
                        "The enrollment no longer exists."));
        if (!EmployeeTraining.RESULT_ENROLLED.equals(enrollment.getResult())) {
            throw new BusinessException("Already in progress",
                    "An enrollment with result " + enrollment.getResult()
                            + " is part of the employee's history and cannot be removed.");
        }
        repository.deleteEnrollment(enrollmentId);
        audit("DELETE", "EmployeeTraining", enrollmentId,
                "Unenrolled " + enrollment.getEmployeeCode() + " from '"
                        + enrollment.getProgramName() + "'");
        publishChange();
    }

    /**
     * Records or updates the outcome of an enrollment. Terminal results
     * (PASSED / FAILED / NO_SHOW) lock the record permanently.
     */
    public void recordResult(long enrollmentId, String result, BigDecimal score,
                             String notes) {
        SecurityService.require(Permissions.TRAINING_MANAGE);
        List<String> errors = new ArrayList<>();
        if (!TrainingRules.ENROLLMENT_RESULTS.contains(result)) {
            errors.add("Unknown training result.");
        }
        if (!TrainingRules.isValidScore(score)) {
            errors.add("Score must be between 0 and 100 with at most two decimals.");
        }
        Validators.maxLength(errors, notes, 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        EmployeeTraining enrollment = repository.findEnrollmentById(enrollmentId)
                .orElseThrow(() -> new BusinessException("Enrollment not found",
                        "The enrollment no longer exists."));
        if (enrollment.isDecided()) {
            throw new BusinessException("Result already recorded",
                    "This enrollment ended as " + enrollment.getResult()
                            + " and is locked history.");
        }

        LocalDate completionDate =
                (EmployeeTraining.RESULT_COMPLETED.equals(result)
                        || EmployeeTraining.RESULT_PASSED.equals(result))
                        ? LocalDate.now() : null;
        repository.recordResult(enrollmentId, result, score, completionDate,
                Validators.normalize(notes));
        audit("UPDATE", "EmployeeTraining", enrollmentId,
                "Result of " + enrollment.getEmployeeCode() + " for '"
                        + enrollment.getProgramName() + "' set to " + result
                        + (score == null ? "" : " (" + score.toPlainString() + "/100)"));
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validateProgram(TrainingProgram program) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, program.getName(), "Program name");
        Validators.maxLength(errors, program.getName(), 150, "Program name");
        Validators.maxLength(errors, program.getTrainerName(), 150, "Trainer");
        Validators.maxLength(errors, program.getDescription(), 2000, "Description");
        Validators.nonNegative(errors, program.getCost(), "Cost");
        if (program.getCapacity() != null && program.getCapacity() < 1) {
            errors.add("Capacity must be at least 1 when set.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        program.setName(Validators.normalize(program.getName()));
        program.setTrainerName(Validators.normalize(program.getTrainerName()));
        program.setDescription(Validators.normalize(program.getDescription()));

        if (repository.programCodeExists(Validators.normalize(program.getCode()),
                program.getId())) {
            throw new ValidationException(List.of(
                    "Program code is already in use."));
        }
    }

    private void validateSession(TrainingSession session) {
        List<String> errors = new ArrayList<>();
        if (session.getProgramId() <= 0) {
            errors.add("Program is required.");
        }
        if (session.getStartDateTime() == null || session.getEndDateTime() == null) {
            errors.add("Session start and end date/time are required.");
        } else {
            if (session.getEndDateTime().isBefore(session.getStartDateTime())) {
                errors.add("Session end cannot be before the start.");
            } else {
                BigDecimal computed = TrainingRules.durationHours(
                        session.getStartDateTime(), session.getEndDateTime());
                session.setDurationHours(computed);
                if (computed != null && computed.signum() == 0) {
                    errors.add("Session must be longer than zero minutes.");
                }
            }
        }
        Validators.maxLength(errors, session.getLocation(), 150, "Location");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        session.setLocation(Validators.normalize(session.getLocation()));

        TrainingProgram program = requireProgram(session.getProgramId());
        if (!TrainingRules.programAcceptsEnrollment(program.getStatus())) {
            throw new BusinessException("Program is closed",
                    "Sessions can only be scheduled for PLANNED or ONGOING programs.");
        }
        session.setProgramName(program.getName());
    }

    private TrainingProgram requireProgram(long programId) {
        return repository.findProgramById(programId).orElseThrow(() ->
                new BusinessException("Program not found",
                        "The training program no longer exists."));
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "TRAINING", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

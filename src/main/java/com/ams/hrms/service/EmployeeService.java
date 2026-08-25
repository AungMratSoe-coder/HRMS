package com.ams.hrms.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Employee;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.EmployeeRepository.Filter;
import com.ams.hrms.repository.EmployeeRepository.HistoryEntry;
import com.ams.hrms.repository.PositionRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.util.ImageUtils;

/**
 * Employee business rules (spec sections 10 and 46): unique code/NRC, sane
 * contact data, join/date logic, position salary-envelope enforcement,
 * self-manager guard, soft-delete status transitions, immutable history and
 * photo storage. Every operation is RBAC-gated and audited.
 */
public class EmployeeService {

    public static final String DATA_SCOPE = "employees";

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository repository;
    private final AuditService auditService;

    /** Position salary envelope needed for envelope validation. */
    public record PositionEnvelope(BigDecimal min, BigDecimal max) {
    }

    private final PositionRepository positionRepository;

    public EmployeeService(EmployeeRepository repository,
                           PositionRepository positionRepository,
                           AuditService auditService) {
        this.repository = repository;
        this.positionRepository = positionRepository;
        this.auditService = auditService;
    }

    public List<Employee> findAll(Filter filter) {
        SecurityService.require(Permissions.EMPLOYEE_VIEW);
        return repository.findAll(filter, selfScopeEmployeeId());
    }

    /** One page of the list screen (server-side pagination). */
    public List<Employee> searchPage(Filter filter, int offset, int limit) {
        SecurityService.require(Permissions.EMPLOYEE_VIEW);
        return repository.findPage(filter, selfScopeEmployeeId(), offset, limit);
    }

    /** Total rows matching the filter; pairs with {@link #searchPage}. */
    public long countMatching(Filter filter) {
        SecurityService.require(Permissions.EMPLOYEE_VIEW);
        return repository.countMatching(filter, selfScopeEmployeeId());
    }

    /**
     * Suggested code for a new employee ({@code EMP-####}, next free number).
     * A suggestion only - the dialog keeps the field editable for sites with
     * external code conventions; uniqueness is enforced on save.
     */
    public String nextEmployeeCode() {
        SecurityService.require(Permissions.EMPLOYEE_CREATE);
        return repository.nextEmployeeCode();
    }

    // ------------------------------------------------------------------
    // Approval scoping (department managers)
    // ------------------------------------------------------------------

    /**
     * Guards leave/overtime decisions: a plain MANAGER (no HR/Finance/Super
     * Admin role) may only decide requests of employees in their own
     * department, never at final (HR) level. Everyone else is unrestricted.
     * Fails closed when the manager's own department cannot be resolved.
     */
    public void requireMayDecideFor(long employeeId, boolean hrLevel) {
        Set<String> roleCodes = SessionContext.roles().stream()
                .map(SessionContext.RoleRef::code)
                .collect(java.util.stream.Collectors.toSet());
        if (!ApprovalScope.isScopedManager(roleCodes)) {
            return;
        }
        if (hrLevel) {
            throw new BusinessException("HR approval required",
                    "Final (HR-level) approval is reserved for HR, Finance "
                            + "and Super Admin accounts.");
        }
        Long viewerDepartment = SessionContext.currentEmployeeId() == null
                ? ApprovalScope.NO_DEPARTMENT
                : repository.findById(SessionContext.currentEmployeeId())
                        .map(Employee::getDepartmentId).orElse(ApprovalScope.NO_DEPARTMENT);
        Long employeeDepartment = repository.findById(employeeId)
                .map(Employee::getDepartmentId).orElse(null);
        if (!ApprovalScope.canDecide(viewerDepartment, employeeDepartment)) {
            throw new BusinessException("Outside your department",
                    "Department managers can only decide requests for "
                            + "employees in their own department.");
        }
    }

    public Employee findById(long id) {
        if (!isOwnRecord(id)) {
            SecurityService.require(Permissions.EMPLOYEE_VIEW);
        }
        Long scope = selfScopeEmployeeId();
        if (scope != null && scope != id) {
            throw notFound(id);
        }
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    public List<HistoryEntry> findHistory(long employeeId) {
        if (!isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.EMPLOYEE_VIEW);
        }
        requireVisible(employeeId);
        return repository.findHistory(employeeId);
    }

    /**
     * Employee record of the signed-in user, when it can be resolved: the
     * explicit account link first, then a unique match on the account email.
     * Needs no directory permission - it is inherently self-scoped.
     */
    public java.util.Optional<Employee> findOwnEmployee() {
        SessionContext.AuthenticatedUser user = SessionContext.currentUser();
        if (user.employeeId() != null) {
            return repository.findById(user.employeeId());
        }
        String email = user.email();
        if (email != null && !email.isBlank()) {
            return repository.findIdByEmail(email).flatMap(repository::findById);
        }
        return java.util.Optional.empty();
    }

    /** True when the given employee record belongs to the signed-in user. */
    public boolean isOwnRecord(long employeeId) {
        Long linked = SessionContext.currentEmployeeId();
        if (linked != null) {
            return linked == employeeId;
        }
        String email = SessionContext.currentUser().email();
        return email != null && !email.isBlank()
                && repository.findIdByEmail(email)
                        .map(id -> id == employeeId)
                        .orElse(false);
    }

    /**
     * Self-service directory scope: a signed-in user whose only role is
     * EMPLOYEE may browse their own record only (explicit account link or,
     * as a fallback, the user email). Null means unrestricted (any
     * staff/manager/admin role). Other services reuse this so every module
     * scopes plain employees identically.
     */
    public Long selfScopeEmployeeId() {
        if (!SessionContext.hasOnlyRole("EMPLOYEE")) {
            return null;
        }
        SessionContext.AuthenticatedUser user = SessionContext.currentUser();
        if (user.employeeId() != null) {
            return user.employeeId();
        }
        String email = user.email();
        if (email == null || email.isBlank()) {
            return -1L;
        }
        return repository.findIdByEmail(email)
                .orElse(-1L);
    }

    /** Blocks scoped (self-service) users from reading someone else's data. */
    public void requireVisible(long employeeId) {
        Long scope = selfScopeEmployeeId();
        if (scope != null && scope != employeeId) {
            throw notFound(employeeId);
        }
    }

    private static BusinessException notFound(long id) {
        return new BusinessException("Employee " + id + " not found",
                "The employee no longer exists.");
    }

    /**
     * Creates or updates an employee (transactional: salary structure +
     * history). Returns the persisted id.
     */
    public long save(Employee employee) {
        boolean isNew = employee.getId() == null;
        SecurityService.require(isNew ? Permissions.EMPLOYEE_CREATE : Permissions.EMPLOYEE_UPDATE);
        validate(employee, isNew);

        if (isNew) {
            long id = repository.insert(employee);
            auditService.record("CREATE", "EMPLOYEE", "Employee", id,
                    "Created employee '" + employee.getCode() + " - " + employee.getFullName() + "'");
            publishChange();
            return id;
        }

        Employee old = repository.findById(employee.getId())
                .orElseThrow(() -> new BusinessException(
                        "Employee " + employee.getId() + " not found",
                        "The employee no longer exists."));
        repository.update(employee, old);
        auditService.record("UPDATE", "EMPLOYEE", "Employee", employee.getId(),
                "Updated employee '" + employee.getCode() + "'");
        publishChange();
        return employee.getId();
    }

    /** Soft status transition with history + audit (activate/deactivate). */
    public void setStatus(long id, String newStatus) {
        SecurityService.require(Permissions.EMPLOYEE_UPDATE);
        Employee employee = repository.findById(id).orElseThrow(() -> new BusinessException(
                "Employee " + id + " not found", "The employee no longer exists."));
        repository.setStatus(id, newStatus);
        auditService.record("STATUS_CHANGE", "EMPLOYEE", "Employee", id,
                "Employee '" + employee.getCode() + "' set to " + newStatus);
        publishChange();
    }

    /** Stores a profile photo and links it to the employee. */
    public void uploadPhoto(long employeeId, Path sourceFile) {
        SecurityService.require(Permissions.EMPLOYEE_PHOTO_UPLOAD);
        Employee employee = repository.findById(employeeId).orElseThrow(() -> new BusinessException(
                "Employee " + employeeId + " not found", "The employee no longer exists."));
        String stored = ImageUtils.storeProfilePhoto(sourceFile, employeeId);
        repository.updatePhotoPath(employeeId, stored);
        auditService.record("UPDATE", "EMPLOYEE", "Employee", employeeId,
                "Uploaded profile photo for '" + employee.getCode() + "'");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(Employee employee, boolean isNew) {
        List<String> errors = EmployeeRules.validate(employee);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        EmployeeRules.normalize(employee);

        if (repository.codeExists(employee.getCode(), employee.getId())) {
            throw new ValidationException(
                    List.of("Employee code '" + employee.getCode() + "' is already in use."));
        }
        if (employee.getNrc() != null
                && repository.nrcExists(employee.getNrc(), employee.getId())) {
            throw new ValidationException(
                    List.of("NRC '" + employee.getNrc() + "' is already registered."));
        }
        enforceSalaryEnvelope(employee);
    }

    /** Basic salary must sit inside the position's envelope when defined. */
    private void enforceSalaryEnvelope(Employee employee) {
        var envelope = positionRepository.findById(employee.getPositionId())
                .map(position -> new PositionEnvelope(position.getMinSalary(), position.getMaxSalary()))
                .orElse(null);
        if (envelope == null) {
            return;
        }
        List<String> errors = EmployeeRules.salaryEnvelopeErrors(
                employee.getBasicSalary(), envelope.min(), envelope.max());
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void publishChange() {
        LOG.debug("Employees changed; publishing event");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged(DepartmentService.DATA_SCOPE));
        EventBus.publish(new Events.DataChanged(PositionService.DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

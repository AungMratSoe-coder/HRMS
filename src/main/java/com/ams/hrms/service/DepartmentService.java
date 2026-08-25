package com.ams.hrms.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.repository.DepartmentRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Department business rules (spec sections 12 and 46): uniqueness of code
 * and name, referential guards before deactivation (data integrity rule 2),
 * RBAC enforcement and audit entries for every mutation.
 */
public class DepartmentService {

    public static final String DATA_SCOPE = "departments";

    private static final Logger LOG = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository repository;
    private final AuditService auditService;

    public DepartmentService(DepartmentRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<Department> findAll(String keyword) {
        SecurityService.require(Permissions.DEPARTMENT_VIEW);
        return repository.findAll(keyword);
    }

    /** Creates or updates a department; returns the persisted id. */
    public long save(Department department) {
        boolean isNew = department.getId() == null;
        SecurityService.require(isNew ? Permissions.DEPARTMENT_CREATE : Permissions.DEPARTMENT_UPDATE);

        validate(department);

        if (isNew) {
            department.setStatus("ACTIVE");
            long id = repository.insert(department);
            auditService.record("CREATE", "ORG", "Department", id,
                    "Created department '" + department.getCode() + " - " + department.getName() + "'");
            publishChange();
            return id;
        }

        repository.update(department);
        auditService.record("UPDATE", "ORG", "Department", department.getId(),
                "Updated department '" + department.getCode() + "'");
        publishChange();
        return department.getId();
    }

    /**
     * Soft-deletes (deactivates) a department. Blocked while active employees
     * or active positions still reference it (spec section 46 rule 2).
     */
    public void setStatus(long id, String status) {
        SecurityService.require(Permissions.DEPARTMENT_UPDATE);
        Department department = repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Department " + id + " not found",
                        "The department no longer exists."));

        if ("INACTIVE".equals(status)) {
            long employees = repository.activeEmployeeCount(id);
            if (employees > 0) {
                throw new BusinessException(
                        "Department " + department.getCode() + " has " + employees + " active employees",
                        "This department still has " + employees
                                + " active employee(s). Reassign them before deactivating.");
            }
            long positions = repository.activePositionCount(id);
            if (positions > 0) {
                throw new BusinessException(
                        "Department " + department.getCode() + " has " + positions + " active positions",
                        "This department still has " + positions
                                + " active position(s). Deactivate or move them first.");
            }
        }

        if (status.equals(department.getStatus())) {
            return;
        }
        repository.setStatus(id, status);
        auditService.record("STATUS_CHANGE", "ORG", "Department", id,
                "Department '" + department.getCode() + "' set to " + status);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(Department department) {
        List<String> errors = new ArrayList<>();

        Validators.required(errors, department.getCode(), "Department code");
        Validators.pattern(errors, department.getCode(), "Department code",
                Validators.CODE_PATTERN, "HR or IT-DEV");
        Validators.required(errors, department.getName(), "Department name");
        Validators.maxLength(errors, department.getName(), 120, "Department name");
        Validators.maxLength(errors, department.getDescription(), 500, "Description");

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        department.setCode(Validators.normalize(department.getCode()).toUpperCase());
        department.setName(Validators.normalize(department.getName()));

        if (repository.codeExists(department.getCode(), department.getId())) {
            throw new ValidationException(
                    List.of("Department code '" + department.getCode() + "' is already in use."));
        }
        if (repository.nameExists(department.getName(), department.getId())) {
            throw new ValidationException(
                    List.of("Department name '" + department.getName() + "' is already in use."));
        }
    }

    private void publishChange() {
        LOG.debug("Departments changed; publishing event");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

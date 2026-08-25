package com.ams.hrms.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Position;
import com.ams.hrms.repository.PositionRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Position business rules (spec sections 13 and 46): uniqueness, salary
 * envelope sanity, referential guard before deactivation (rule 3), RBAC and
 * audit for every mutation.
 */
public class PositionService {

    public static final String DATA_SCOPE = "positions";

    private static final Logger LOG = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository repository;
    private final AuditService auditService;

    public PositionService(PositionRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<Position> findAll(String keyword) {
        SecurityService.require(Permissions.POSITION_VIEW);
        return repository.findAll(keyword);
    }

    /** Creates or updates a position; returns the persisted id. */
    public long save(Position position) {
        boolean isNew = position.getId() == null;
        SecurityService.require(isNew ? Permissions.POSITION_CREATE : Permissions.POSITION_UPDATE);

        validate(position);

        if (isNew) {
            position.setStatus("ACTIVE");
            long id = repository.insert(position);
            auditService.record("CREATE", "ORG", "Position", id,
                    "Created position '" + position.getCode() + " - " + position.getName() + "'");
            publishChange();
            return id;
        }

        repository.update(position);
        auditService.record("UPDATE", "ORG", "Position", position.getId(),
                "Updated position '" + position.getCode() + "'");
        publishChange();
        return position.getId();
    }

    /**
     * Soft-deletes (deactivates) a position; blocked while active employees
     * still hold it (spec section 46 rule 3).
     */
    public void setStatus(long id, String status) {
        SecurityService.require(Permissions.POSITION_UPDATE);
        Position position = repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Position " + id + " not found",
                        "The position no longer exists."));

        if ("INACTIVE".equals(status)) {
            long employees = repository.activeEmployeeCount(id);
            if (employees > 0) {
                throw new BusinessException(
                        "Position " + position.getCode() + " has " + employees + " active employees",
                        "This position still has " + employees
                                + " active employee(s). Reassign them before deactivating.");
            }
        }

        if (status.equals(position.getStatus())) {
            return;
        }
        repository.setStatus(id, status);
        auditService.record("STATUS_CHANGE", "ORG", "Position", id,
                "Position '" + position.getCode() + "' set to " + status);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(Position position) {
        List<String> errors = new ArrayList<>();

        Validators.required(errors, position.getCode(), "Position code");
        Validators.pattern(errors, position.getCode(), "Position code",
                Validators.CODE_PATTERN, "IT-DEV or HR-MGR");
        Validators.required(errors, position.getName(), "Position name");
        Validators.maxLength(errors, position.getName(), 120, "Position name");
        if (position.getDepartmentId() == null) {
            errors.add("Department is required.");
        }
        Validators.maxLength(errors, position.getDescription(), 500, "Description");

        BigDecimal min = Validators.parseMoney(errors,
                position.getMinSalary() == null ? "" : position.getMinSalary().toPlainString(),
                "Minimum salary");
        BigDecimal max = Validators.parseMoney(errors,
                position.getMaxSalary() == null ? "" : position.getMaxSalary().toPlainString(),
                "Maximum salary");
        Validators.nonNegative(errors, min, "Minimum salary");
        Validators.nonNegative(errors, max, "Maximum salary");
        Validators.salaryRange(errors, min, max);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        position.setCode(Validators.normalize(position.getCode()).toUpperCase());
        position.setName(Validators.normalize(position.getName()));

        if (repository.codeExists(position.getCode(), position.getId())) {
            throw new ValidationException(
                    List.of("Position code '" + position.getCode() + "' is already in use."));
        }
        if (position.getDepartmentId() != null
                && repository.nameExistsInDepartment(position.getName(),
                        position.getDepartmentId(), position.getId())) {
            throw new ValidationException(
                    List.of("Position name '" + position.getName()
                            + "' already exists in this department."));
        }
    }

    private void publishChange() {
        LOG.debug("Positions changed; publishing event");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Asset;
import com.ams.hrms.model.AssetAssignment;
import com.ams.hrms.repository.AssetRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.validator.Validators;

/**
 * Asset management (spec section 24): asset catalogue with lifecycle states,
 * assignment to employees (transactional pair: assignment row + asset status),
 * returns that route damaged assets into repair, lost handling and overdue
 * flagging. Every operation is RBAC-gated and audited.
 */
public class AssetService {

    public static final String DATA_SCOPE = "assets";

    private static final Logger LOG = LoggerFactory.getLogger(AssetService.class);

    private final AssetRepository repository;
    private final AuditService auditService;

    public AssetService(AssetRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<Asset> findAssets(String keyword, String category, String status) {
        SecurityService.require(Permissions.ASSET_VIEW);
        return repository.findAssets(keyword, category, status);
    }

    public List<AssetAssignment> findAssignments(Long assetId, Long employeeId,
                                                 String status, String keyword) {
        SecurityService.require(Permissions.ASSET_VIEW);
        return repository.findAssignments(assetId, employeeId, status, keyword);
    }

    /** Flags open assignments past their due date; called at startup/tools. */
    public int refreshOverdue() {
        int flagged = repository.markOverdueAssignments(LocalDate.now());
        if (flagged > 0) {
            LOG.info("Flagged {} asset assignment(s) as OVERDUE", flagged);
            publishChange();
        }
        return flagged;
    }

    // ------------------------------------------------------------------
    // Asset catalogue
    // ------------------------------------------------------------------

    /** Creates or updates an asset; returns the persisted id. */
    public long saveAsset(Asset asset) {
        boolean isNew = asset.getId() == null;
        SecurityService.require(Permissions.ASSET_MANAGE);
        validateAsset(asset);

        if (isNew) {
            long id = repository.insertAsset(asset);
            repository.updateAssetCode(id, "AST-" + String.format("%04d", id));
            audit("CREATE", "Asset", id,
                    "Registered asset AST-" + String.format("%04d", id)
                            + " '" + asset.getName() + "' (" + asset.getCategory() + ")");
            publishChange();
            return id;
        }
        Asset existing = requireAsset(asset.getId());
        if ("RETIRED".equals(existing.getStatus()) || "LOST".equals(existing.getStatus())) {
            throw new BusinessException("Asset is retired",
                    "Retired or lost assets cannot be edited.");
        }
        repository.updateAsset(asset);
        audit("UPDATE", "Asset", asset.getId(),
                "Updated asset '" + asset.getCode() + "'");
        publishChange();
        return asset.getId();
    }

    /** Manual status change (repair/retire/lost); ASSIGNED only via assign(). */
    public void setAssetStatus(long assetId, String targetStatus) {
        SecurityService.require(Permissions.ASSET_MANAGE);
        Asset asset = requireAsset(assetId);
        if (!AssetRules.canTransitionManually(asset.getStatus(), targetStatus)) {
            throw new BusinessException("Transition not allowed",
                    "An asset cannot move from " + asset.getStatus()
                            + " to " + targetStatus + " directly.");
        }
        repository.updateAssetStatus(assetId, targetStatus);
        audit("STATUS_CHANGE", "Asset", assetId,
                "Asset '" + asset.getCode() + "' set to " + targetStatus);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Assignments
    // ------------------------------------------------------------------

    /**
     * Assigns an AVAILABLE asset to an employee in one transaction:
     * the assignment row and the asset status flip together.
     */
    public long assign(long assetId, long employeeId, LocalDate assignedDate,
                       LocalDate dueReturnDate, String notes) {
        SecurityService.require(Permissions.ASSET_ASSIGN);
        List<String> errors = new ArrayList<>();
        if (employeeId <= 0) {
            errors.add("Employee is required.");
        }
        if (assignedDate == null) {
            errors.add("Assigned date is required.");
        }
        if (dueReturnDate != null && assignedDate != null
                && dueReturnDate.isBefore(assignedDate)) {
            errors.add("Due return date cannot be before the assigned date.");
        }
        Validators.maxLength(errors, notes, 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Asset asset = requireAsset(assetId);
        if (!AssetRules.ASSET_AVAILABLE.equals(asset.getStatus())) {
            throw new BusinessException("Asset not available",
                    "'" + asset.getCode() + "' is currently " + asset.getStatus()
                            + " and cannot be assigned.");
        }

        AssetAssignment assignment = new AssetAssignment();
        assignment.setAssetId(assetId);
        assignment.setEmployeeId(employeeId);
        assignment.setAssignedDate(assignedDate);
        assignment.setDueReturnDate(dueReturnDate);
        assignment.setNotes(Validators.normalize(notes));

        long id = TransactionManager.execute(tx -> {
            long created = repository.insertAssignment(assignment);
            repository.updateAssetStatus(assetId, AssetRules.ASSET_ASSIGNED);
            return created;
        });
        audit("ASSIGN", "AssetAssignment", id,
                "Assigned '" + asset.getCode() + "' (" + asset.getName()
                        + ") to " + holderLabel(employeeId)
                        + (dueReturnDate == null ? "" : ", due back "
                                + dueReturnDate));
        publishChange();
        return id;
    }

    /**
     * Returns an open assignment in one transaction: closes the assignment
     * and routes the asset (damaged goes to repair, otherwise available).
     */
    public void returnAsset(long assignmentId, LocalDate returnedDate,
                            String conditionOnReturn, String notes) {
        SecurityService.require(Permissions.ASSET_ASSIGN);
        List<String> errors = new ArrayList<>();
        if (returnedDate == null) {
            errors.add("Returned date is required.");
        }
        if (!AssetRules.isValidCondition(conditionOnReturn)
                || conditionOnReturn.equals("NEW")) {
            errors.add("Returned condition must be GOOD, FAIR, POOR or DAMAGED.");
        }
        Validators.maxLength(errors, notes, 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AssetAssignment assignment = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> new BusinessException("Assignment not found",
                        "The asset assignment no longer exists."));
        if (!assignment.isOpen()) {
            throw new BusinessException("Already closed",
                    "This assignment is already " + assignment.getStatus() + ".");
        }
        if (returnedDate.isBefore(assignment.getAssignedDate())) {
            throw new ValidationException(List.of(
                    "Returned date cannot be before the assigned date."));
        }
        String nextAssetStatus = AssetRules.statusAfterReturn(conditionOnReturn);

        TransactionManager.execute(tx -> {
            repository.returnAssignment(tx, assignmentId, returnedDate,
                    conditionOnReturn, notes);
            repository.updateAssetStatus(assignment.getAssetId(), nextAssetStatus);
            return null;
        });
        audit("RETURN", "AssetAssignment", assignmentId,
                "Returned '" + assignment.getAssetCode() + "' from "
                        + assignment.getEmployeeName() + " (condition "
                        + conditionOnReturn + ")");
        publishChange();
    }

    /** Marks an open assignment and its asset LOST; history preserved. */
    public void markLost(long assignmentId, String notes) {
        SecurityService.require(Permissions.ASSET_MANAGE);
        List<String> errors = new ArrayList<>();
        Validators.maxLength(errors, notes, 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AssetAssignment assignment = repository.findAssignmentById(assignmentId)
                .orElseThrow(() -> new BusinessException("Assignment not found",
                        "The asset assignment no longer exists."));
        if (!assignment.isOpen()) {
            throw new BusinessException("Already closed",
                    "This assignment is already " + assignment.getStatus() + ".");
        }

        TransactionManager.execute(tx -> {
            repository.markAssignmentStatus(assignmentId, AssetAssignment.STATUS_LOST);
            repository.updateAssetStatus(assignment.getAssetId(), AssetRules.ASSET_LOST);
            return null;
        });
        audit("STATUS_CHANGE", "AssetAssignment", assignmentId,
                "Assignment of '" + assignment.getAssetCode() + "' marked LOST");
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validateAsset(Asset asset) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, asset.getName(), "Asset name");
        Validators.maxLength(errors, asset.getName(), 150, "Asset name");
        if (!AssetRules.isValidCategory(asset.getCategory())) {
            errors.add("Category is invalid.");
        }
        Validators.maxLength(errors, asset.getSerialNumber(), 100, "Serial number");
        Validators.maxLength(errors, asset.getNotes(), 500, "Notes");
        Validators.nonNegative(errors, asset.getPurchaseCost(), "Purchase cost");
        if (asset.getPurchaseDate() != null && asset.getWarrantyExpiry() != null
                && asset.getWarrantyExpiry().isBefore(asset.getPurchaseDate())) {
            errors.add("Warranty expiry cannot be before the purchase date.");
        }
        if (!AssetRules.isValidCondition(asset.getConditionStatus())) {
            errors.add("Condition is invalid.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        asset.setName(Validators.normalize(asset.getName()));
        asset.setSerialNumber(Validators.normalize(asset.getSerialNumber()).isEmpty()
                ? null : Validators.normalize(asset.getSerialNumber()));
        asset.setNotes(Validators.normalize(asset.getNotes()));

        if (repository.assetCodeExists(Validators.normalize(asset.getCode()),
                asset.getId())) {
            throw new ValidationException(List.of(
                    "Asset code is already in use."));
        }
    }

    private String holderLabel(long employeeId) {
        return new com.ams.hrms.repository.Sql().first(
                "SELECT CONCAT(employee_code, ' - ', full_name) FROM employees WHERE id = ?",
                rs -> rs.getString(1), employeeId).orElse("employee #" + employeeId);
    }

    private Asset requireAsset(long assetId) {
        return repository.findAssetById(assetId).orElseThrow(() ->
                new BusinessException("Asset not found",
                        "The asset no longer exists."));
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "ASSET", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

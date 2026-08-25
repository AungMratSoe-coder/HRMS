package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Asset;
import com.ams.hrms.model.AssetAssignment;

/** Asset and assignment persistence (spec section 24). */
public class AssetRepository {

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    private static final String HOLDER_CODE_SUBQUERY =
            "(SELECT h.employee_code FROM asset_assignments aa "
                    + "JOIN employees h ON h.id = aa.employee_id "
                    + "WHERE aa.asset_id = a.id AND aa.status IN ('ASSIGNED', 'OVERDUE') "
                    + "ORDER BY aa.id DESC LIMIT 1)";

    private static final String HOLDER_NAME_SUBQUERY =
            "(SELECT h.full_name FROM asset_assignments aa "
                    + "JOIN employees h ON h.id = aa.employee_id "
                    + "WHERE aa.asset_id = a.id AND aa.status IN ('ASSIGNED', 'OVERDUE') "
                    + "ORDER BY aa.id DESC LIMIT 1)";

    private static final String SELECT_ASSET =
            "SELECT a.id, a.asset_code, a.asset_name, a.category, a.serial_number, "
                    + "a.purchase_date, a.purchase_cost, a.warranty_expiry, "
                    + "a.condition_status, a.status, a.notes, "
                    + HOLDER_CODE_SUBQUERY + " AS holder_code, "
                    + HOLDER_NAME_SUBQUERY + " AS holder_name "
                    + "FROM assets a";

    public List<Asset> findAssets(String keyword, String category, String status) {
        StringBuilder sql = new StringBuilder(SELECT_ASSET).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (a.asset_name LIKE CONCAT('%', ?, '%') "
                    + "OR a.asset_code LIKE CONCAT('%', ?, '%') "
                    + "OR a.serial_number LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND a.category = ?");
            params.add(category);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY a.id DESC");
        return new Sql().list(sql.toString(), this::mapAsset, params.toArray());
    }

    public Optional<Asset> findAssetById(long id) {
        return new Sql().first(SELECT_ASSET + " WHERE a.id = ?", this::mapAsset, id);
    }

    public boolean assetCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM assets WHERE asset_code = ? AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertAsset(Asset asset) {
        return new Sql().executeInsert(
                "INSERT INTO assets (asset_code, asset_name, category, serial_number, "
                        + "purchase_date, purchase_cost, warranty_expiry, condition_status, "
                        + "notes) VALUES ('TMP', ?, ?, ?, ?, ?, ?, ?, ?)",
                asset.getName(), asset.getCategory(), asset.getSerialNumber(),
                asset.getPurchaseDate(), asset.getPurchaseCost(), asset.getWarrantyExpiry(),
                asset.getConditionStatus(), asset.getNotes());
    }

    public void updateAssetCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE assets SET asset_code = ? WHERE id = ?", code, id);
    }

    public void updateAsset(Asset asset) {
        new Sql().executeUpdate(
                "UPDATE assets SET asset_name = ?, category = ?, serial_number = ?, "
                        + "purchase_date = ?, purchase_cost = ?, warranty_expiry = ?, "
                        + "condition_status = ?, notes = ? WHERE id = ?",
                asset.getName(), asset.getCategory(), asset.getSerialNumber(),
                asset.getPurchaseDate(), asset.getPurchaseCost(), asset.getWarrantyExpiry(),
                asset.getConditionStatus(), asset.getNotes(), asset.getId());
    }

    public void updateAssetStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE assets SET status = ? WHERE id = ?", status, id);
    }

    // ------------------------------------------------------------------
    // Assignments
    // ------------------------------------------------------------------

    private static final String SELECT_ASSIGNMENT =
            "SELECT aa.id, aa.asset_id, aa.employee_id, aa.assigned_date, "
                    + "aa.due_return_date, aa.returned_date, aa.condition_on_return, "
                    + "aa.notes, aa.status, aa.assigned_by, "
                    + "a.asset_code, a.asset_name, e.employee_code, e.full_name AS employee_name "
                    + "FROM asset_assignments aa "
                    + "JOIN assets a ON a.id = aa.asset_id "
                    + "JOIN employees e ON e.id = aa.employee_id";

    public List<AssetAssignment> findAssignments(Long assetId, Long employeeId,
                                                 String status, String keyword) {
        StringBuilder sql = new StringBuilder(SELECT_ASSIGNMENT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (assetId != null) {
            sql.append(" AND aa.asset_id = ?");
            params.add(assetId);
        }
        if (employeeId != null) {
            sql.append(" AND aa.employee_id = ?");
            params.add(employeeId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND aa.status = ?");
            params.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (a.asset_name LIKE CONCAT('%', ?, '%') "
                    + "OR a.asset_code LIKE CONCAT('%', ?, '%') "
                    + "OR e.full_name LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        sql.append(" ORDER BY aa.id DESC");
        return new Sql().list(sql.toString(), this::mapAssignment, params.toArray());
    }

    public Optional<AssetAssignment> findAssignmentById(long id) {
        return new Sql().first(SELECT_ASSIGNMENT + " WHERE aa.id = ?",
                this::mapAssignment, id);
    }

    public long insertAssignment(AssetAssignment assignment) {
        return new Sql().executeInsert(
                "INSERT INTO asset_assignments (asset_id, employee_id, assigned_date, "
                        + "due_return_date, notes, status, assigned_by) "
                        + "VALUES (?, ?, ?, ?, ?, 'ASSIGNED', ?)",
                assignment.getAssetId(), assignment.getEmployeeId(),
                assignment.getAssignedDate(), assignment.getDueReturnDate(),
                assignment.getNotes(), com.ams.hrms.security.SessionContext.currentUserId());
    }

    /** Closes an assignment with the return metadata (caller's transaction). */
    public void returnAssignment(Sql sql, long id, LocalDate returnedDate,
                                 String conditionOnReturn, String notes) {
        sql.executeUpdate(
                "UPDATE asset_assignments SET status = 'RETURNED', returned_date = ?, "
                        + "condition_on_return = ?, notes = CONCAT(COALESCE(notes, ''), ?) "
                        + "WHERE id = ?",
                returnedDate, conditionOnReturn, notes == null || notes.isBlank() ? ""
                        : " | Return: " + notes.trim(),
                id);
    }

    public void markAssignmentStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE asset_assignments SET status = ? WHERE id = ?", status, id);
    }

    /** Flags open assignments whose due date has passed; called at startup/tools. */
    public int markOverdueAssignments(LocalDate today) {
        return new Sql().executeUpdate(
                "UPDATE asset_assignments SET status = 'OVERDUE' "
                        + "WHERE status = 'ASSIGNED' AND due_return_date IS NOT NULL "
                        + "AND due_return_date < ?",
                today);
    }

    /**
     * Separation support: closes every open assignment of an employee
     * (condition GOOD) and releases their assets back to AVAILABLE.
     * Runs inside the caller's transaction.
     *
     * @return number of assignments closed
     */
    public int closeOpenForEmployee(Sql sql, long employeeId, LocalDate returnedDate) {
        int closed = sql.executeUpdate(
                "UPDATE asset_assignments SET status = 'RETURNED', returned_date = ?, "
                        + "condition_on_return = 'GOOD', "
                        + "notes = CONCAT(COALESCE(notes, ''), ' | Auto-returned on separation') "
                        + "WHERE employee_id = ? AND status IN ('ASSIGNED', 'OVERDUE')",
                returnedDate, employeeId);
        sql.executeUpdate(
                "UPDATE assets SET status = 'AVAILABLE' WHERE status = 'ASSIGNED' "
                        + "AND id IN (SELECT DISTINCT asset_id FROM asset_assignments "
                        + "WHERE employee_id = ?)",
                employeeId);
        return closed;
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private Asset mapAsset(ResultSet rs) throws SQLException {
        Asset asset = new Asset();
        asset.setId(rs.getLong("id"));
        asset.setCode(rs.getString("asset_code"));
        asset.setName(rs.getString("asset_name"));
        asset.setCategory(rs.getString("category"));
        asset.setSerialNumber(rs.getString("serial_number"));
        asset.setPurchaseDate(rs.getObject("purchase_date", LocalDate.class));
        asset.setPurchaseCost(rs.getBigDecimal("purchase_cost"));
        asset.setWarrantyExpiry(rs.getObject("warranty_expiry", LocalDate.class));
        asset.setConditionStatus(rs.getString("condition_status"));
        asset.setStatus(rs.getString("status"));
        asset.setNotes(rs.getString("notes"));
        asset.setHolderCode(rs.getString("holder_code"));
        asset.setHolderName(rs.getString("holder_name"));
        return asset;
    }

    private AssetAssignment mapAssignment(ResultSet rs) throws SQLException {
        AssetAssignment assignment = new AssetAssignment();
        assignment.setId(rs.getLong("id"));
        assignment.setAssetId(rs.getLong("asset_id"));
        assignment.setEmployeeId(rs.getLong("employee_id"));
        assignment.setAssignedDate(rs.getObject("assigned_date", LocalDate.class));
        assignment.setDueReturnDate(rs.getObject("due_return_date", LocalDate.class));
        assignment.setReturnedDate(rs.getObject("returned_date", LocalDate.class));
        assignment.setConditionOnReturn(rs.getString("condition_on_return"));
        assignment.setNotes(rs.getString("notes"));
        assignment.setStatus(rs.getString("status"));
        long assignedBy = rs.getLong("assigned_by");
        assignment.setAssignedBy(rs.wasNull() ? null : assignedBy);
        assignment.setAssetCode(rs.getString("asset_code"));
        assignment.setAssetName(rs.getString("asset_name"));
        assignment.setEmployeeCode(rs.getString("employee_code"));
        assignment.setEmployeeName(rs.getString("employee_name"));
        return assignment;
    }
}

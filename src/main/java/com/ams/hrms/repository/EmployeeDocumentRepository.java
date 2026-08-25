package com.ams.hrms.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.model.EmployeeDocument;

/**
 * Document metadata persistence (spec section 25). Files live on the file
 * system; rows are soft-deleted via the status column.
 */
public class EmployeeDocumentRepository {

    private static final String SELECT =
            "SELECT id, employee_id, document_type, file_name, file_path, file_size, mime_type, "
                    + "expiry_date, notes, status, created_at AS uploaded_at "
                    + "FROM employee_documents";

    /** Active documents for one employee, newest first. */
    public List<EmployeeDocument> findByEmployee(long employeeId) {
        return new Sql().list(
                SELECT + " WHERE employee_id = ? AND status <> 'DELETED' ORDER BY id DESC",
                this::mapRow, employeeId);
    }

    /** Active documents expiring within {@code days} from today (expiry alerts). */
    public List<EmployeeDocument> findExpiring(int days) {
        return new Sql().list(
                SELECT + " WHERE status = 'ACTIVE' AND expiry_date IS NOT NULL "
                        + "AND expiry_date BETWEEN CURDATE() AND CURDATE() + INTERVAL ? DAY "
                        + "ORDER BY expiry_date",
                this::mapRow, days);
    }

    private static final String SEARCH_SELECT =
            "SELECT d.id, d.employee_id, d.document_type, d.file_name, d.file_path, d.file_size, "
                    + "d.mime_type, d.expiry_date, d.notes, d.status, "
                    + "d.created_at AS uploaded_at, e.employee_code AS employee_code, "
                    + "e.full_name AS employee_name "
                    + "FROM employee_documents d "
                    + "JOIN employees e ON e.id = d.employee_id";

    /**
     * Module-wide listing with employee identity: keyword matches file name,
     * employee code/name and notes; empty type/status means all (DELETED only
     * when explicitly requested).
     */
    public List<EmployeeDocument> search(String keyword, String documentType, String status) {
        StringBuilder sql = new StringBuilder(SEARCH_SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (d.file_name LIKE ? OR e.employee_code LIKE ? "
                    + "OR e.full_name LIKE ? OR d.notes LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (documentType != null && !documentType.isBlank()) {
            sql.append(" AND d.document_type = ?");
            params.add(documentType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND d.status = ?");
            params.add(status);
        } else {
            sql.append(" AND d.status <> 'DELETED'");
        }
        sql.append(" ORDER BY d.id DESC");
        return new Sql().list(sql.toString(), this::mapJoinedRow, params.toArray());
    }

    public long insert(EmployeeDocument document) {
        return new Sql().executeInsert(
                "INSERT INTO employee_documents (employee_id, document_type, file_name, file_path, "
                        + "file_size, mime_type, expiry_date, notes, status, uploaded_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                document.getEmployeeId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getFilePath(),
                document.getFileSize(),
                document.getMimeType(),
                document.getExpiryDate(),
                document.getNotes(),
                com.ams.hrms.security.SessionContext.currentUserId());
    }

    /** Soft transitions: ARCHIVED or DELETED (file stays on disk by policy). */
    public void setStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE employee_documents SET status = ? WHERE id = ?", status, id);
    }

    /** Marks expired ACTIVE documents (run at startup/daily; Phase 23 notifies). */
    public int markExpired() {
        return new Sql().executeUpdate(
                "UPDATE employee_documents SET status = 'EXPIRED' "
                        + "WHERE status = 'ACTIVE' AND expiry_date < CURDATE()");
    }

    private EmployeeDocument mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        EmployeeDocument document = new EmployeeDocument();
        document.setId(rs.getLong("id"));
        document.setEmployeeId(rs.getLong("employee_id"));
        document.setDocumentType(rs.getString("document_type"));
        document.setFileName(rs.getString("file_name"));
        document.setFilePath(rs.getString("file_path"));
        long size = rs.getLong("file_size");
        document.setFileSize(rs.wasNull() ? null : size);
        document.setMimeType(rs.getString("mime_type"));
        document.setExpiryDate(rs.getObject("expiry_date", LocalDate.class));
        document.setNotes(rs.getString("notes"));
        document.setStatus(rs.getString("status"));
        document.setUploadedAt(rs.getObject("uploaded_at", LocalDateTime.class));
        return document;
    }

    private EmployeeDocument mapJoinedRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        EmployeeDocument document = mapRow(rs);
        document.setEmployeeCode(rs.getString("employee_code"));
        document.setEmployeeName(rs.getString("employee_name"));
        return document;
    }
}

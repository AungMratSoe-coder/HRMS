package com.ams.hrms.service;

import java.nio.file.Path;
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
import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.repository.EmployeeDocumentRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.FileStorage;
import com.ams.hrms.validator.Validators;

/**
 * Employee document management (spec section 25): upload with type/size
 * validation, expiry tracking, archive/soft-delete, RBAC and audit.
 */
public class DocumentService {

    public static final String DATA_SCOPE = "documents";
    public static final int EXPIRY_WARNING_DAYS = 30;

    private static final Logger LOG = LoggerFactory.getLogger(DocumentService.class);

    public static final Set<String> DOCUMENT_TYPES = Set.of(
            "NRC", "PASSPORT", "CONTRACT", "CERTIFICATE", "RESUME",
            "TRAINING_CERTIFICATE", "OTHER");

    private final EmployeeDocumentRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public DocumentService(EmployeeDocumentRepository repository, AuditService auditService,
                           EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    public List<EmployeeDocument> findByEmployee(long employeeId) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.EMPLOYEE_VIEW);
        }
        return repository.findByEmployee(employeeId);
    }

    /** ACTIVE documents expiring within the warning window (expiry alerts). */
    public List<EmployeeDocument> findExpiring() {
        SecurityService.require(Permissions.EMPLOYEE_VIEW);
        return repository.findExpiring(EXPIRY_WARNING_DAYS);
    }

    /** Module-wide listing with employee identity (Documents screen). */
    public List<EmployeeDocument> search(String keyword, String documentType, String status) {
        SecurityService.require(Permissions.DOCUMENT_MANAGE);
        return repository.search(keyword, documentType, status);
    }

    /** Validates, stores the file and records metadata; returns the new id. */
    public long upload(long employeeId, String documentType, Path sourceFile,
                       LocalDate expiryDate, String notes) {
        SecurityService.require(Permissions.DOCUMENT_MANAGE);

        List<String> errors = new ArrayList<>();
        if (documentType == null || !DOCUMENT_TYPES.contains(documentType)) {
            errors.add("Document type is invalid.");
        }
        if (sourceFile == null) {
            errors.add("Please choose a file to upload.");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            errors.add("Expiry date cannot be in the past.");
        }
        Validators.maxLength(errors, notes, 500, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        String storedPath = FileStorage.storeDocument(sourceFile, employeeId);
        EmployeeDocument document = new EmployeeDocument();
        document.setEmployeeId(employeeId);
        document.setDocumentType(documentType);
        document.setFileName(sourceFile.getFileName().toString());
        document.setFilePath(storedPath);
        try {
            document.setFileSize(java.nio.file.Files.size(sourceFile));
        } catch (java.io.IOException e) {
            document.setFileSize(null);
        }
        document.setMimeType(FileStorage.mimeOf(document.getFileName()));
        document.setExpiryDate(expiryDate);
        document.setNotes(Validators.normalize(notes));

        long id = repository.insert(document);
        auditService.record("CREATE", "EMPLOYEE", "EmployeeDocument", id,
                "Uploaded " + documentType + " '" + document.getFileName()
                        + "' for employee #" + employeeId);
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        return id;
    }

    /** Soft-archives a document (file remains on disk by policy). */
    public void archive(long documentId) {
        SecurityService.require(Permissions.DOCUMENT_MANAGE);
        setStatusInternal(documentId, "ARCHIVED");
    }

    /** Soft-deletes a document (metadata kept, status DELETED). */
    public void delete(long documentId) {
        SecurityService.require(Permissions.DOCUMENT_MANAGE);
        setStatusInternal(documentId, "DELETED");
    }

    /** Marks lapsed documents EXPIRED; called at startup (Phase 23 adds alerts). */
    public int refreshExpiredStatuses() {
        int marked = repository.markExpired();
        if (marked > 0) {
            LOG.info("Marked {} document(s) as EXPIRED", marked);
        }
        return marked;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void setStatusInternal(long documentId, String status) {
        repository.setStatus(documentId, status);
        auditService.record("STATUS_CHANGE", "EMPLOYEE", "EmployeeDocument", documentId,
                "Document set to " + status);
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }
}

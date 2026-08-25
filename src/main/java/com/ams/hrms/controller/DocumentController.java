package com.ams.hrms.controller;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.util.UiThread;

/** View-controller for employee documents; all calls run off the EDT. */
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    public void load(long employeeId, Consumer<List<EmployeeDocument>> onSuccess) {
        UiThread.executeAsync("Load documents",
                () -> documentService.findByEmployee(employeeId), onSuccess);
    }

    public void loadAll(String keyword, String documentType, String status,
                        Consumer<List<EmployeeDocument>> onSuccess) {
        UiThread.executeAsync("Load document list",
                () -> documentService.search(keyword, documentType, status), onSuccess);
    }

    public void upload(long employeeId, String type, Path file, LocalDate expiry,
                       String notes, Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Upload document",
                () -> documentService.upload(employeeId, type, file, expiry, notes),
                onSuccess, onError);
    }

    public void archive(long documentId, Runnable onDone) {
        UiThread.executeAsync("Archive document",
                () -> {
                    documentService.archive(documentId);
                    return null;
                },
                result -> onDone.run());
    }

    public void delete(long documentId, Runnable onDone) {
        UiThread.executeAsync("Delete document",
                () -> {
                    documentService.delete(documentId);
                    return null;
                },
                result -> onDone.run());
    }
}

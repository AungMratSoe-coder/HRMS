package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.repository.AuditRepository.AuditRow;
import com.ams.hrms.repository.AuditRepository.Filter;
import com.ams.hrms.repository.AuditRepository.UserOption;
import com.ams.hrms.service.AuditService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Audit Log module (spec section 28); off-EDT calls. */
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Loads one page plus the matching total in a single background task. */
    public void loadPage(Filter filter, int page, int pageSize,
                         Consumer<PageResult> onSuccess) {
        UiThread.executeAsync("Search audit log",
                () -> {
                    long total = auditService.countMatching(filter);
                    int offset = Math.max(0, (page - 1) * pageSize);
                    List<AuditRow> rows = auditService.search(filter, offset, pageSize);
                    return new PageResult(rows, total);
                },
                onSuccess);
    }

    public void loadFilterOptions(Consumer<FilterOptions> onSuccess) {
        UiThread.executeAsync("Load audit filter options",
                () -> new FilterOptions(
                        auditService.distinctModules(),
                        auditService.distinctActions(),
                        auditService.distinctUsers()),
                onSuccess);
    }

    public record PageResult(List<AuditRow> rows, long totalMatching) {
    }

    public record FilterOptions(List<String> modules, List<String> actions,
                                List<UserOption> users) {
    }
}

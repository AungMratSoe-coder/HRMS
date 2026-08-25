package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.report.ReportDefinition;
import com.ams.hrms.report.ReportFilter;
import com.ams.hrms.report.ReportResult;
import com.ams.hrms.service.ReportService;
import com.ams.hrms.util.UiThread;

/**
 * View-controller for the Reports module (spec sections 27, 44): every
 * database or rendering call runs off the EDT through {@link UiThread}.
 */
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    public ReportController() {
        this(ServiceRegistry.reportService());
    }

    public void loadCatalog(Consumer<List<ReportDefinition>> onSuccess) {
        UiThread.executeAsync("Load report catalog", () -> List.copyOf(reportService.catalog()),
                onSuccess);
    }

    public void generate(ReportDefinition definition, ReportFilter filter,
                         Consumer<ReportResult> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Generate " + definition.title(),
                () -> reportService.generate(definition, filter), onSuccess, onError);
    }

    /** Renders PDF bytes for the given result (permission-checked). */
    public void exportPdf(ReportResult result, Consumer<byte[]> onSuccess,
                          Consumer<Exception> onError) {
        UiThread.executeAsync("Export PDF",
                () -> reportService.exportPdf(result), onSuccess, onError);
    }

    /** Renders Excel bytes for the given result (permission-checked). */
    public void exportExcel(ReportResult result, Consumer<byte[]> onSuccess,
                            Consumer<Exception> onError) {
        UiThread.executeAsync("Export Excel",
                () -> reportService.exportExcel(result), onSuccess, onError);
    }
}

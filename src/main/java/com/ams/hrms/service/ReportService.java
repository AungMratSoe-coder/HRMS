package com.ams.hrms.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.report.ExcelReportWriter;
import com.ams.hrms.report.PdfReportWriter;
import com.ams.hrms.report.ReportDefinition;
import com.ams.hrms.report.ReportFilter;
import com.ams.hrms.report.ReportResult;
import com.ams.hrms.repository.ReportRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;

/**
 * Report generation and export (spec sections 27-28). Enforces
 * {@code REPORT_VIEW} for generation and {@code REPORT_EXPORT} for every
 * export/print payload; validates filter combinations before querying; the
 * repository does all database access; exports are audited.
 */
public class ReportService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);

    /** Longest single range accepted by range-bound reports (days). */
    private static final int MAX_RANGE_DAYS = 366;

    /** Reports that scan day-level rows - their range is capped. */
    private static final List<ReportDefinition> RANGE_CAPPED = List.of(
            ReportDefinition.ATTENDANCE_SUMMARY,
            ReportDefinition.LATE_REPORT,
            ReportDefinition.ABSENCE_REPORT,
            ReportDefinition.TURNOVER_REPORT);

    private final ReportRepository reportRepository;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    /** All reports a signed-in {@code REPORT_VIEW} holder can generate. */
    public List<ReportDefinition> catalog() {
        SecurityService.require(Permissions.REPORT_VIEW);
        return Arrays.asList(ReportDefinition.values());
    }

    /** Generates a report; heavy queries run off the EDT via controllers. */
    public ReportResult generate(ReportDefinition definition, ReportFilter filter) {
        SecurityService.require(Permissions.REPORT_VIEW);
        validate(definition, filter);
        LOG.info("Generating report '{}' [{}]", definition.title(), filter.describe());
        return reportRepository.generate(definition, filter,
                SessionContext.currentUser().username());
    }

    /**
     * Renders the result as PDF bytes.
     *
     * @throws BusinessException when rendering fails
     */
    public byte[] exportPdf(ReportResult result) {
        SecurityService.require(Permissions.REPORT_EXPORT);
        try {
            byte[] pdf = PdfReportWriter.write(result);
            auditExport(result, "PDF", pdf.length);
            return pdf;
        } catch (IOException e) {
            LOG.error("PDF export failed for '{}': {}", result.title(), e.getMessage(), e);
            throw new BusinessException("PDF export failed",
                    "Could not render the report as PDF. Please try again.");
        }
    }

    /**
     * Renders the result as Excel bytes.
     *
     * @throws BusinessException when rendering fails
     */
    public byte[] exportExcel(ReportResult result) {
        SecurityService.require(Permissions.REPORT_EXPORT);
        try {
            byte[] workbook = ExcelReportWriter.write(result);
            auditExport(result, "Excel", workbook.length);
            return workbook;
        } catch (IOException e) {
            LOG.error("Excel export failed for '{}': {}", result.title(), e.getMessage(), e);
            throw new BusinessException("Excel export failed",
                    "Could not render the report as an Excel file. Please try again.");
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(ReportDefinition definition, ReportFilter filter) {
        List<String> errors = new ArrayList<>();

        if (definition.needsDateRange()) {
            if (filter.dateFrom() == null || filter.dateTo() == null) {
                errors.add("Select both a start date and an end date.");
            } else if (filter.dateFrom().isAfter(filter.dateTo())) {
                errors.add("The start date must be on or before the end date.");
            } else if (RANGE_CAPPED.contains(definition)
                    && ChronoUnit.DAYS.between(filter.dateFrom(), filter.dateTo())
                            > MAX_RANGE_DAYS) {
                errors.add("The date range must not exceed " + MAX_RANGE_DAYS + " days.");
            }
        }
        if (!definition.supportsKeyword()
                && filter.keyword() != null && !filter.keyword().isBlank()) {
            errors.add("This report does not support keyword search.");
        }
        if (definition.statusOptions().length == 0 && filter.status() != null
                && !filter.status().isBlank()) {
            errors.add("This report does not support status filtering.");
        } else if (definition.statusOptions().length > 0 && filter.status() != null
                && !filter.status().isBlank()
                && !List.of(definition.statusOptions()).contains(filter.status().trim())) {
            errors.add("Unknown status filter: " + filter.status());
        }
        if (errors.isEmpty()) {
            return;
        }
        throw new ValidationException(errors);
    }

    private void auditExport(ReportResult result, String format, int byteLength) {
        auditService.record(
                "EXPORT_" + format.toUpperCase(),
                "REPORTS",
                "report",
                null,
                "Exported '" + result.title() + "' (" + result.dataRowCount()
                        + " rows, " + byteLength + " bytes)");
    }
}

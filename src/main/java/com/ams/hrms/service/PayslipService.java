package com.ams.hrms.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.PayrollRepository;
import com.ams.hrms.repository.PayrollRepository.PayrollRow;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.report.PayslipPdfGenerator;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Generates PDF payslips (spec section 21): pulls payroll + company settings,
 * delegates to {@link PayslipPdfGenerator}, saves to the documents directory.
 */
public class PayslipService {

    private static final Logger LOG = LoggerFactory.getLogger(PayslipService.class);

    private final PayrollRepository payrollRepository;

    public PayslipService(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    public Path generatePayslip(long payrollId, Path outputDirectory) {
        SecurityService.require(Permissions.PAYSLIP_GENERATE);

        PayrollRow row = findByPayrollId(payrollId)
                .orElseThrow(() -> new BusinessException(
                        "Payroll not found",
                        "The payroll record no longer exists."));

        var data = new PayslipPdfGenerator.PayslipData(
                settingText("company.name", "Company"),
                settingText("company.address", ""),
                row.employeeCode(),
                row.fullName(),
                row.departmentName(),
                "-",
                row.periodName(),
                row.basicSalary(),
                row.grossSalary().subtract(row.basicSalary()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                row.grossSalary(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                row.totalDeduction(),
                row.totalDeduction(),
                row.netSalary(),
                row.currency());

        String fileName = "payslip_" + row.employeeCode() + "_"
                + row.periodName().replace("-", "_") + ".pdf";
        Path outputPath = outputDirectory.resolve(fileName);

        try {
            PayslipPdfGenerator.generate(data, outputPath);
            LOG.info("Payslip generated: {}", outputPath);
        } catch (IOException e) {
            throw new BusinessException("Failed to generate payslip",
                    "Could not write the payslip file. Please check disk permissions.");
        }
        return outputPath;
    }

    /** Finds a payroll row by id across all periods. */
    private Optional<PayrollRow> findByPayrollId(long payrollId) {
        return payrollRepository.allPeriods().stream()
                .flatMap(period -> payrollRepository.findByPeriod(period.id()).stream())
                .filter(row -> row.id() == payrollId)
                .findFirst();
    }

    private String settingText(String key, String fallback) {
        return new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                rs -> rs.getString(1), key).orElse(fallback);
    }
}

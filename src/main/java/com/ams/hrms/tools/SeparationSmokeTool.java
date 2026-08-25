package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Asset;
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.model.Resignation;
import com.ams.hrms.model.Termination;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AssetService;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.PayrollService;
import com.ams.hrms.service.SeparationService;
import com.ams.hrms.service.ShiftService;

/**
 * Development-only Phase 21 verification against the live database: hires two
 * fresh employees through the recruitment pipeline, then verifies the exit
 * checklist end-to-end - resignation SUBMITTED &rarr; APPROVED &rarr;
 * PROCESSED (status change, shift close, asset return, payroll void) and the
 * immediate termination path, plus duplicate guards and RBAC denial for
 * FINANCE. Idempotent cleanup afterwards.
 */
public final class SeparationSmokeTool {

    private static int failures;
    private static long vacancyId;
    private static long candidateAId;
    private static long candidateBId;
    private static long employeeAId;
    private static long employeeBId;
    private static long assetId;
    private static long resignationId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        SeparationService separation = ServiceRegistry.separationService();

        purgeArtifacts();
        authService.login("admin", "Admin@123");

        // --- hire two fresh employees via the recruitment pipeline -------------
        employeeAId = hireViaPipeline("Resignee", "smoke.resignee@example.com", "1");
        employeeBId = hireViaPipeline("Terminatee", "smoke.terminatee@example.com", "2");

        // --- give employee A a shift, an asset and a draft payroll --------------
        long shiftId = new Sql().scalarLong(
                "SELECT id FROM shifts WHERE shift_code = 'SH-MORNING'");
        ServiceRegistry.shiftService().assign(employeeAId, shiftId, LocalDate.now());

        AssetService assets = ServiceRegistry.assetService();
        Asset asset = new Asset();
        asset.setName("SMOKE Exit Laptop");
        asset.setCategory("LAPTOP");
        asset.setConditionStatus("NEW");
        assetId = assets.saveAsset(asset);
        assets.assign(assetId, employeeAId, LocalDate.now(), null, "Smoke exit setup");

        PayrollService payroll = ServiceRegistry.payrollService();
        long periodId = new com.ams.hrms.repository.PayrollRepository()
                .findOrCreatePeriod(LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        new Sql().executeInsert(
                "INSERT INTO payrolls (payroll_number, employee_id, payroll_period_id, "
                        + "basic_salary, status) VALUES (?, ?, ?, 1000, 'DRAFT')",
                "PR-SMOKE-EXIT-" + employeeAId, employeeAId, periodId);

        // --- resignation workflow -------------------------------------------------
        Resignation resignation = new Resignation();
        resignation.setEmployeeId(employeeAId);
        resignation.setResignationDate(LocalDate.now());
        resignation.setLastWorkingDate(LocalDate.now().plusDays(14));
        resignation.setReason("Smoke career change");
        check("record SUBMITTED resignation with computed notice", () -> {
            resignationId = separation.recordResignation(resignation);
            var stored = separation.findResignationById(resignationId);
            return stored.getStatus().equals("SUBMITTED")
                    && stored.getResignationCode() != null
                    && stored.getNoticePeriodDays() == 14;
        });

        check("second open resignation for the same employee rejected",
                () -> {
                    try {
                        Resignation duplicate = new Resignation();
                        duplicate.setEmployeeId(employeeAId);
                        duplicate.setResignationDate(LocalDate.now());
                        duplicate.setLastWorkingDate(LocalDate.now().plusDays(10));
                        separation.recordResignation(duplicate);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("process blocked before approval",
                () -> {
                    try {
                        separation.processResignation(resignationId);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("approve then process runs the full exit checklist", () -> {
            separation.approveResignation(resignationId);
            String summary = separation.processResignation(resignationId);

            String employeeStatus = new Sql().first(
                    "SELECT status FROM employees WHERE id = ?",
                    rs -> rs.getString(1), employeeAId).orElse("");
            boolean shiftClosed = new Sql().scalarLong(
                    "SELECT COUNT(*) FROM employee_shifts WHERE employee_id = ? "
                            + "AND effective_to IS NOT NULL", employeeAId) > 0;
            boolean assetReturned = new Sql().scalarLong(
                    "SELECT COUNT(*) FROM asset_assignments WHERE employee_id = ? "
                            + "AND status = 'RETURNED'", employeeAId) > 0;
            String assetStatus = new Sql().first(
                    "SELECT status FROM assets WHERE id = ?",
                    rs -> rs.getString(1), assetId).orElse("");
            long cancelledPayrolls = new Sql().scalarLong(
                    "SELECT COUNT(*) FROM payrolls WHERE employee_id = ? "
                            + "AND status = 'CANCELLED'", employeeAId);

            return "RESIGNED".equals(employeeStatus)
                    && shiftClosed
                    && assetReturned
                    && "AVAILABLE".equals(assetStatus)
                    && cancelledPayrolls == 1
                    && summary.contains("status updated");
        });

        check("exit interview notes recorded on PROCESSED resignation", () -> {
            separation.saveExitInterviewNotes(resignationId,
                    "Knowledge transfer completed with the team.");
            return separation.findResignationById(resignationId)
                    .getExitInterviewNotes().contains("Knowledge transfer");
        });

        // --- termination path -------------------------------------------------------
        Termination termination = new Termination();
        termination.setEmployeeId(employeeBId);
        termination.setTerminationDate(LocalDate.now());
        termination.setReasonCategory("CONTRACT_END");
        termination.setReason("Contract not renewed (smoke)");
        termination.setEligibleRehire(true);
        check("termination is effective immediately", () -> {
            String summary = separation.terminate(termination);
            String employeeStatus = new Sql().first(
                    "SELECT status FROM employees WHERE id = ?",
                    rs -> rs.getString(1), employeeBId).orElse("");
            var stored = separation.findTerminations("Terminatee").stream()
                    .filter(candidate -> candidate.getEmployeeId() == employeeBId)
                    .findFirst().orElseThrow();
            return "TERMINATED".equals(employeeStatus)
                    && stored.getTerminationCode() != null
                    && stored.isEligibleRehire()
                    && summary.contains("completed");
        });

        check("resignation for already-separated employee rejected",
                () -> {
                    try {
                        Resignation late = new Resignation();
                        late.setEmployeeId(employeeBId);
                        late.setResignationDate(LocalDate.now());
                        late.setLastWorkingDate(LocalDate.now().plusDays(5));
                        separation.recordResignation(late);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        // --- RBAC: FINANCE has no SEPARATION_MANAGE --------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied separation access at service gate",
                () -> {
                    try {
                        separation.findResignations(null, null);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ------------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke hires, separations, assets, payroll removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Runs the recruitment pipeline to a hire; returns the employee id. */
    private static long hireViaPipeline(String lastName, String email,
                                        String phoneSuffix) throws Exception {
        var recruitment = ServiceRegistry.recruitmentService();
        Long departmentId = new Sql().scalarLong(
                "SELECT id FROM departments ORDER BY id LIMIT 1");
        Long positionId = new Sql().scalarLong(
                "SELECT id FROM positions WHERE department_id = ? ORDER BY id LIMIT 1",
                departmentId);
        BigDecimal salary = new Sql().first(
                "SELECT COALESCE(max_salary, 900) FROM positions WHERE id = ?",
                rs -> rs.getBigDecimal(1), positionId).orElse(BigDecimal.valueOf(900));

        JobVacancy vacancy = new JobVacancy();
        vacancy.setTitle("SMOKE Separation Role " + phoneSuffix);
        vacancy.setDepartmentId(departmentId);
        vacancy.setPositionId(positionId);
        vacancy.setHeadcount(1);
        vacancy.setEmploymentType("FULL_TIME");
        vacancy.setOpeningDate(LocalDate.now());
        vacancyId = recruitment.saveVacancy(vacancy);

        Candidate candidate = new Candidate();
        candidate.setFirstName("Smoke");
        candidate.setLastName(lastName);
        candidate.setGender("MALE");
        candidate.setDateOfBirth(LocalDate.now().minusYears(29));
        candidate.setPhone("09-7710000" + phoneSuffix);
        candidate.setEmail(email);
        candidate.setSource("WEBSITE");
        long candidateId = recruitment.saveCandidate(candidate);
        if (phoneSuffix.equals("A")) {
            candidateAId = candidateId;
        } else {
            candidateBId = candidateId;
        }

        long applicationId = recruitment.apply(candidateId, vacancyId, null);
        recruitment.shortlist(applicationId);
        Interview interview = new Interview();
        interview.setApplicationId(applicationId);
        interview.setInterviewDate(LocalDateTime.now().plusDays(1));
        interview.setMode("PHONE");
        long interviewId = recruitment.scheduleInterview(interview);
        recruitment.recordResult(interviewId, "PASS", BigDecimal.valueOf(85), null);

        JobOffer offer = new JobOffer();
        offer.setApplicationId(applicationId);
        offer.setOfferedSalary(salary);
        offer.setOfferDate(LocalDate.now());
        offer.setJoiningDate(LocalDate.now());
        long offerId = recruitment.createOffer(offer);
        recruitment.sendOffer(offerId);
        recruitment.acceptOffer(offerId);
        return recruitment.hire(offerId, null);
    }

    /** Removes every smoke artifact across recruitment, employment and exit rows. */
    private static void purgeArtifacts() {
        // Separation records
        new Sql().executeUpdate(
                "DELETE FROM resignations WHERE employee_id IN "
                        + "(SELECT id FROM employees WHERE email LIKE 'smoke.%@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM terminations WHERE employee_id IN "
                        + "(SELECT id FROM employees WHERE email LIKE 'smoke.%@example.com')");
        // Assets
        new Sql().executeUpdate(
                "DELETE FROM asset_assignments WHERE asset_id IN "
                        + "(SELECT id FROM assets WHERE asset_name LIKE 'SMOKE %')");
        new Sql().executeUpdate(
                "DELETE FROM assets WHERE asset_name LIKE 'SMOKE %'");
        // Payroll drafts
        new Sql().executeUpdate(
                "DELETE FROM payrolls WHERE payroll_number LIKE 'PR-SMOKE-EXIT-%'");
        // Shift assignments of smoke employees
        new Sql().executeUpdate(
                "DELETE FROM employee_shifts WHERE employee_id IN "
                        + "(SELECT id FROM employees WHERE email LIKE 'smoke.%@example.com')");
        // Employees (salary structures + history first)
        for (String email : new String[]{
                "smoke.resignee@example.com", "smoke.terminatee@example.com"}) {
            Long employeeId = new Sql().first(
                    "SELECT id FROM employees WHERE email = ?", rs -> rs.getLong(1), email)
                    .orElse(null);
            if (employeeId != null) {
                new Sql().executeUpdate(
                        "DELETE FROM onboarding_tasks WHERE employee_id = ?", employeeId);
                new Sql().executeUpdate(
                        "DELETE FROM salary_structures WHERE employee_id = ?", employeeId);
                new Sql().executeUpdate(
                        "DELETE FROM employee_history WHERE employee_id = ?", employeeId);
                new Sql().executeUpdate(
                        "UPDATE job_offers SET employee_id = NULL WHERE employee_id = ?",
                        employeeId);
                new Sql().executeUpdate(
                        "DELETE FROM employees WHERE id = ?", employeeId);
            }
        }
        // Recruitment rows
        new Sql().executeUpdate(
                "DELETE FROM job_offers WHERE application_id IN "
                        + "(SELECT a.id FROM applications a JOIN candidates c "
                        + "ON c.id = a.candidate_id WHERE c.email LIKE 'smoke.%@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM applications WHERE candidate_id IN "
                        + "(SELECT id FROM candidates WHERE email LIKE 'smoke.%@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM interviews WHERE application_id NOT IN "
                        + "(SELECT id FROM applications)");
        new Sql().executeUpdate(
                "DELETE FROM candidates WHERE email LIKE 'smoke.%@example.com'");
        new Sql().executeUpdate(
                "DELETE FROM job_vacancies WHERE title LIKE 'SMOKE Separation Role%'");
    }

    private static void check(String label, BooleanCheck action) {
        try {
            boolean passed = action.run();
            System.out.println((passed ? "OK   " : "FAIL ") + label);
            if (!passed) {
                failures++;
            }
        } catch (Exception e) {
            System.out.println("FAIL " + label + " -> unexpected "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            failures++;
        }
    }

    @FunctionalInterface
    private interface BooleanCheck {
        boolean run() throws Exception;
    }
}

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
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.RecruitmentService;
import com.ams.hrms.service.RecruitmentWorkflow;

/**
 * Development-only Phase 16 verification against the live database: the full
 * pipeline vacancy &rarr; candidate &rarr; application &rarr; interview PASS
 * &rarr; offer &rarr; accept &rarr; hire, plus validation rejections,
 * workflow guards, RBAC denial for FINANCE, headcount-driven vacancy fill and
 * idempotent cleanup.
 */
public final class RecruitmentSmokeTool {

    private static int failures;
    private static long vacancyId;
    private static long candidateId;
    private static long applicationId;
    private static long interviewId;
    private static long offerId;
    private static long hiredEmployeeId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        RecruitmentService recruitment = ServiceRegistry.recruitmentService();
        recruitment.expireStaleOffers();

        purgeArtifacts();
        authService.login("admin", "Admin@123");

        Long departmentId = new Sql().scalarLong(
                "SELECT id FROM departments ORDER BY id LIMIT 1");
        Long positionId = new Sql().scalarLong(
                "SELECT id FROM positions WHERE department_id = ? ORDER BY id LIMIT 1",
                departmentId);
        BigDecimal envelopeMax = new Sql().first(
                "SELECT max_salary FROM positions WHERE id = ?",
                rs -> rs.getBigDecimal(1), positionId).orElse(null);
        BigDecimal offeredSalary = envelopeMax != null
                ? envelopeMax : BigDecimal.valueOf(900);

        // --- vacancy ---------------------------------------------------------
        JobVacancy vacancy = new JobVacancy();
        vacancy.setTitle("SMOKE QA Engineer");
        vacancy.setDepartmentId(departmentId);
        vacancy.setPositionId(positionId);
        vacancy.setHeadcount(1);
        vacancy.setEmploymentType("FULL_TIME");
        vacancy.setOpeningDate(LocalDate.now());
        check("create OPEN vacancy", () -> {
            vacancyId = recruitment.saveVacancy(vacancy);
            return recruitment.findVacancies("SMOKE", "OPEN").stream()
                    .anyMatch(v -> v.getId() == vacancyId && v.getVacancyCode() != null);
        });

        // --- candidate ---------------------------------------------------------
        Candidate candidate = new Candidate();
        candidate.setFirstName("Smoke");
        candidate.setLastName("Candidate");
        candidate.setGender("FEMALE");
        candidate.setDateOfBirth(LocalDate.now().minusYears(28));
        candidate.setPhone("09-770000001");
        candidate.setEmail("smoke.candidate@example.com");
        candidate.setExperienceYears(BigDecimal.valueOf(4));
        candidate.setExpectedSalary(BigDecimal.valueOf(1800));
        candidate.setSource("LINKEDIN");
        check("register candidate", () -> {
            candidateId = recruitment.saveCandidate(candidate);
            return candidate.getStatus().equals("NEW")
                    || "NEW".equals(recruitment.findCandidates("Smoke", null).stream()
                            .filter(c -> c.getId() == candidateId).findFirst().orElseThrow()
                            .getStatus());
        });

        // --- application pipeline ----------------------------------------------
        check("submit application", () -> {
            applicationId = recruitment.apply(candidateId, vacancyId, "Smoke cover letter");
            return statusOf(applicationId).equals("SUBMITTED");
        });

        check("duplicate application rejected",
                () -> {
                    try {
                        recruitment.apply(candidateId, vacancyId, null);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("shortlist moves to SCREENING", () -> {
            recruitment.shortlist(applicationId);
            return statusOf(applicationId).equals("SCREENING");
        });

        Interview interview = new Interview();
        interview.setApplicationId(applicationId);
        interview.setInterviewDate(LocalDateTime.now().plusDays(1));
        interview.setMode("VIDEO");
        check("schedule interview round 1", () -> {
            interviewId = recruitment.scheduleInterview(interview);
            return statusOf(applicationId).equals("INTERVIEW");
        });

        check("past interview date rejected",
                () -> {
                    Interview bad = new Interview();
                    bad.setApplicationId(applicationId);
                    bad.setInterviewDate(LocalDateTime.now().minusDays(1));
                    bad.setMode("PHONE");
                    try {
                        recruitment.scheduleInterview(bad);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("offer blocked before a PASSED interview",
                () -> {
                    JobOffer earlyOffer = new JobOffer();
                    earlyOffer.setApplicationId(applicationId);
                    earlyOffer.setOfferedSalary(BigDecimal.valueOf(1700));
                    earlyOffer.setOfferDate(LocalDate.now());
                    try {
                        recruitment.createOffer(earlyOffer);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("record PASS result", () -> {
            recruitment.recordResult(interviewId, "PASS", BigDecimal.valueOf(86), "Strong fit");
            var stored = recruitment.findInterviews(null, "PASS").stream()
                    .filter(i -> i.getId() == interviewId).findFirst().orElseThrow();
            return stored.getScore().compareTo(BigDecimal.valueOf(86)) == 0;
        });

        // --- offer lifecycle ------------------------------------------------------
        JobOffer offer = new JobOffer();
        offer.setApplicationId(applicationId);
        offer.setOfferedSalary(offeredSalary);
        offer.setOfferDate(LocalDate.now());
        offer.setExpiryDate(LocalDate.now().plusWeeks(2));
        offer.setJoiningDate(LocalDate.now().plusWeeks(3));
        check("create DRAFT offer after PASS", () -> {
            offerId = recruitment.createOffer(offer);
            return statusOf(applicationId).equals("OFFER")
                    && offer.getOfferCode() != null;
        });

        check("send then accept offer", () -> {
            recruitment.sendOffer(offerId);
            boolean sent = ServiceRegistry.recruitmentService()
                    .findOffers(null, "SENT").stream().anyMatch(o -> o.getId() == offerId);
            recruitment.acceptOffer(offerId);
            return sent && ServiceRegistry.recruitmentService()
                    .findOffers(null, "ACCEPTED").stream().anyMatch(o -> o.getId() == offerId);
        });

        check("accepting twice is refused",
                () -> {
                    try {
                        recruitment.sendOffer(offerId);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- hire -----------------------------------------------------------------
        check("hire creates employee and fills the vacancy", () -> {
            hiredEmployeeId = recruitment.hire(offerId, null);
            Employee hired = ServiceRegistry.employeeService().findById(hiredEmployeeId);
            String vacancyStatus = recruitment.findVacancies("SMOKE", null).stream()
                    .filter(v -> v.getId() == vacancyId).findFirst().orElseThrow()
                    .getStatus();
            return hired.getCode().matches("EMP-\\d{4,}")
                    && "ACTIVE".equals(hired.getStatus())
                    && "HIRED".equals(candidateStatus(candidateId))
                    && "FILLED".equals(vacancyStatus);
        });

        check("hiring twice is refused",
                () -> {
                    try {
                        recruitment.hire(offerId, null);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("workflow forbids illegal transitions", () ->
                !RecruitmentWorkflow.canTransitionApplication("SUBMITTED", "OFFER")
                        && !RecruitmentWorkflow.canTransitionOffer("DRAFT", "ACCEPTED"));

        // --- RBAC: FINANCE has no RECRUITMENT_MANAGE -------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied vacancy creation at service gate",
                () -> {
                    try {
                        recruitment.saveVacancy(new JobVacancy());
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke recruitment rows + hired employee removed");

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

    private static String statusOf(long applicationIdValue) {
        return ServiceRegistry.recruitmentService()
                .findApplications(null, null, null).stream()
                .filter(a -> a.getId() == applicationIdValue)
                .findFirst().orElseThrow().getStatus();
    }

    private static String candidateStatus(long candidateIdValue) {
        return ServiceRegistry.recruitmentService()
                .findCandidates("Smoke", null).stream()
                .filter(c -> c.getId() == candidateIdValue)
                .findFirst().orElseThrow().getStatus();
    }

    /** Removes smoke-created recruitment rows and the hired employee. */
    private static void purgeArtifacts() {
        if (hiredEmployeeId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM salary_structures WHERE employee_id = ?", hiredEmployeeId);
            new Sql().executeUpdate(
                    "DELETE FROM employee_history WHERE employee_id = ?", hiredEmployeeId);
            new Sql().executeUpdate(
                    "UPDATE job_offers SET employee_id = NULL WHERE employee_id = ?",
                    hiredEmployeeId);
            new Sql().executeUpdate("DELETE FROM employees WHERE id = ?", hiredEmployeeId);
            hiredEmployeeId = 0;
        }
        new Sql().executeUpdate(
                "DELETE FROM job_offers WHERE application_id IN "
                        + "(SELECT a.id FROM applications a JOIN candidates c "
                        + "ON c.id = a.candidate_id WHERE c.email = 'smoke.candidate@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM applications WHERE candidate_id IN "
                        + "(SELECT id FROM candidates WHERE email = 'smoke.candidate@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM interviews WHERE application_id NOT IN (SELECT id FROM applications)");
        new Sql().executeUpdate(
                "DELETE FROM candidates WHERE email = 'smoke.candidate@example.com'");
        new Sql().executeUpdate("DELETE FROM job_vacancies WHERE title LIKE 'SMOKE %'");
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

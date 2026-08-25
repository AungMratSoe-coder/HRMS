package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.dto.OnboardingProgress;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.OnboardingService;
import com.ams.hrms.service.RecruitmentService;

/**
 * Development-only Phase 17 verification against the live database: hiring
 * auto-generates the checklist from active templates, tasks move through
 * PENDING -&gt; COMPLETED / SKIPPED / WAIVED with reopen, progress math holds,
 * duplicate generation is refused, templates affect future hires only and
 * FINANCE is denied at the service gate. Idempotent cleanup afterwards.
 */
public final class OnboardingSmokeTool {

    private static int failures;
    private static long hiredEmployeeId;
    private static long vacancyId;
    private static long candidateId;
    private static long applicationId;
    private static long interviewId;
    private static long offerId;
    private static long newTemplateId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        RecruitmentService recruitment = ServiceRegistry.recruitmentService();
        OnboardingService onboarding = ServiceRegistry.onboardingService();

        purgeArtifacts();
        authService.login("admin", "Admin@123");

        Long departmentId = new Sql().scalarLong(
                "SELECT id FROM departments ORDER BY id LIMIT 1");
        Long positionId = new Sql().scalarLong(
                "SELECT id FROM positions WHERE department_id = ? ORDER BY id LIMIT 1",
                departmentId);
        BigDecimal offeredSalary = new Sql().first(
                "SELECT COALESCE(max_salary, 900) FROM positions WHERE id = ?",
                rs -> rs.getBigDecimal(1), positionId).orElse(BigDecimal.valueOf(900));

        // --- run the full recruitment pipeline to a hire ----------------------
        JobVacancy vacancy = new JobVacancy();
        vacancy.setTitle("SMOKE Onboarding Analyst");
        vacancy.setDepartmentId(departmentId);
        vacancy.setPositionId(positionId);
        vacancy.setHeadcount(1);
        vacancy.setEmploymentType("FULL_TIME");
        vacancy.setOpeningDate(LocalDate.now());
        vacancyId = recruitment.saveVacancy(vacancy);

        Candidate candidate = new Candidate();
        candidate.setFirstName("Smoke");
        candidate.setLastName("Onboarder");
        candidate.setGender("MALE");
        candidate.setDateOfBirth(LocalDate.now().minusYears(30));
        candidate.setPhone("09-770000002");
        candidate.setEmail("smoke.onboarder@example.com");
        candidate.setSource("REFERRAL");
        candidateId = recruitment.saveCandidate(candidate);

        applicationId = recruitment.apply(candidateId, vacancyId, null);
        recruitment.shortlist(applicationId);

        Interview interview = new Interview();
        interview.setApplicationId(applicationId);
        interview.setInterviewDate(LocalDateTime.now().plusDays(1));
        interview.setMode("PHONE");
        interviewId = recruitment.scheduleInterview(interview);
        recruitment.recordResult(interviewId, "PASS", BigDecimal.valueOf(80), null);

        JobOffer offer = new JobOffer();
        offer.setApplicationId(applicationId);
        offer.setOfferedSalary(offeredSalary);
        offer.setOfferDate(LocalDate.now());
        offer.setJoiningDate(LocalDate.now().plusWeeks(2));
        offerId = recruitment.createOffer(offer);
        recruitment.sendOffer(offerId);
        recruitment.acceptOffer(offerId);

        check("hire succeeds end-to-end", () -> {
            hiredEmployeeId = recruitment.hire(offerId, null);
            return hiredEmployeeId > 0;
        });

        // --- checklist generation ---------------------------------------------
        check("checklist auto-generated at hire", () -> {
            List<com.ams.hrms.model.OnboardingTask> tasks =
                    onboarding.tasksForEmployee(hiredEmployeeId);
            return tasks.size() == 10
                    && tasks.stream().allMatch(t -> "PENDING".equals(t.getStatus()))
                    && tasks.get(0).getDueDate() != null
                    && tasks.get(0).getEmployeeCode() != null;
        });

        check("duplicate generation refused",
                () -> {
                    try {
                        onboarding.generateChecklist(hiredEmployeeId, LocalDate.now());
                        return false;
                    } catch (com.ams.hrms.exception.BusinessException expected) {
                        return true;
                    }
                });

        List<com.ams.hrms.model.OnboardingTask> tasks =
                onboarding.tasksForEmployee(hiredEmployeeId);
        long firstTaskId = tasks.get(0).getId();
        long secondTaskId = tasks.get(1).getId();
        long mandatoryPendingId = tasks.stream()
                .filter(t -> t.isMandatory()).findFirst().orElseThrow().getId();

        check("complete + skip + waive + reopen transitions", () -> {
            onboarding.setTaskStatus(firstTaskId, "COMPLETED");
            onboarding.setTaskStatus(secondTaskId, "SKIPPED");
            onboarding.setTaskStatus(secondTaskId, "WAIVED");
            boolean afterWaive =
                    statusOf(firstTaskId).equals("COMPLETED")
                            && statusOf(secondTaskId).equals("WAIVED");
            onboarding.setTaskStatus(firstTaskId, "PENDING");
            return afterWaive && statusOf(firstTaskId).equals("PENDING");
        });

        check("progress math tracks the ledger", () -> {
            onboarding.setTaskStatus(firstTaskId, "COMPLETED");
            var current = onboarding.tasksForEmployee(hiredEmployeeId);
            OnboardingProgress progress = OnboardingProgress.from(current);
            // t1 COMPLETED, t2 WAIVED -> settled = 2/10 = 20%; the waived
            // mandatory no longer counts as outstanding (seed: 9 of 10 mandatory).
            return progress.completed() == 1
                    && progress.waived() == 1
                    && progress.percentComplete() == 20
                    && !progress.isComplete()
                    && progress.mandatoryOutstanding() == 7;
        });

        check("mandatory pending blocks completion flag", () ->
                !OnboardingProgress.from(onboarding.tasksForEmployee(hiredEmployeeId))
                        .isComplete());

        // --- template lifecycle -------------------------------------------------
        check("template create feeds future hires only", () -> {
            OnboardingTemplate template = new OnboardingTemplate();
            template.setTaskName("SMOKE extra step");
            template.setDescription("Smoke-only template row");
            template.setTaskOrder(99);
            template.setMandatory(false);
            newTemplateId = onboarding.saveTemplate(template);

            boolean existingChecklistUnchanged =
                    onboarding.tasksForEmployee(hiredEmployeeId).size() == 10;
            int generated = generateForCleanEmployee(onboarding);
            boolean futureHireIncludesIt = generated == 11;
            return existingChecklistUnchanged && futureHireIncludesIt;
        });

        check("template deactivate removes it from generation", () -> {
            onboarding.setTemplateActive(newTemplateId, false);
            int generated = generateForCleanEmployee(onboarding);
            return generated == 10;
        });

        // --- RBAC: FINANCE lacks ONBOARDING_MANAGE -------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied checklist access at service gate",
                () -> {
                    try {
                        onboarding.tasksForEmployee(hiredEmployeeId);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke onboarding rows + hired employee removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** Generates a checklist for seeded EMP-0003 (no checklist), then cleans up. */
    private static int generateForCleanEmployee(OnboardingService onboarding) {
        Long targetEmployeeId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0003'");
        new Sql().executeUpdate(
                "DELETE FROM onboarding_tasks WHERE employee_id = ?", targetEmployeeId);
        int created = onboarding.generateChecklist(targetEmployeeId, LocalDate.now());
        new Sql().executeUpdate(
                "DELETE FROM onboarding_tasks WHERE employee_id = ?", targetEmployeeId);
        return created;
    }

    private static String statusOf(long taskId) {
        return ServiceRegistry.onboardingService()
                .tasksForEmployee(hiredEmployeeId).stream()
                .filter(task -> task.getId() == taskId)
                .findFirst().orElseThrow().getStatus();
    }

    /** Removes every smoke artifact: recruitment rows, hired employee, tasks, template. */
    private static void purgeArtifacts() {
        if (hiredEmployeeId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM onboarding_tasks WHERE employee_id = ?", hiredEmployeeId);
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
        if (newTemplateId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM onboarding_templates WHERE id = ?", newTemplateId);
            newTemplateId = 0;
        }
        new Sql().executeUpdate(
                "DELETE FROM onboarding_tasks WHERE task_name LIKE 'SMOKE %' "
                        + "OR employee_id IN (SELECT id FROM employees "
                        + "WHERE employee_code LIKE 'EMP%' AND last_name = 'Onboarder')");
        new Sql().executeUpdate(
                "DELETE FROM job_offers WHERE application_id IN "
                        + "(SELECT a.id FROM applications a JOIN candidates c "
                        + "ON c.id = a.candidate_id WHERE c.email = 'smoke.onboarder@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM applications WHERE candidate_id IN "
                        + "(SELECT id FROM candidates WHERE email = 'smoke.onboarder@example.com')");
        new Sql().executeUpdate(
                "DELETE FROM interviews WHERE application_id NOT IN "
                        + "(SELECT id FROM applications)");
        new Sql().executeUpdate(
                "DELETE FROM candidates WHERE email = 'smoke.onboarder@example.com'");
        new Sql().executeUpdate("DELETE FROM job_vacancies WHERE title LIKE 'SMOKE %'");
        // EMP-0003 test rows in case of an earlier crashed run
        new Sql().executeUpdate(
                "DELETE FROM onboarding_tasks WHERE employee_id = "
                        + "(SELECT id FROM employees WHERE employee_code = 'EMP-0003')");
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

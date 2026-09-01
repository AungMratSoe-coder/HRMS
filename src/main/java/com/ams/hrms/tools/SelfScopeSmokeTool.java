package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.OvertimeRepository;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;

/**
 * Verifies self-service data scoping (privacy): a plain EMPLOYEE account
 * sees only its own attendance / leave / overtime / payslip rows, files
 * requests for itself even when the UI sends another employee id, and
 * cannot read other people's records. Also exercises the admin
 * account-to-employee link API including the duplicate-owner guard.
 * Idempotent: recovers from earlier runs and deactivates its test user.
 */
public final class SelfScopeSmokeTool {

    private static final String SMOKE_USERNAME = "selfscope-user";
    private static final String SMOKE_EMAIL = "selfscope-smoke@example.com";
    private static final String FIRST_PASSWORD = "Start@123";
    private static final String OWN_PASSWORD = "Scoped@123";
    private static final String TARGET_CODE = "EMP-0006";

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService auth = ServiceRegistry.authService();
        var userService = ServiceRegistry.userService();
        var employeeService = ServiceRegistry.employeeService();
        var leaveService = ServiceRegistry.leaveService();
        var overtimeService = ServiceRegistry.overtimeService();
        var attendanceService = ServiceRegistry.attendanceService();
        var payrollService = ServiceRegistry.payrollService();
        UserRepository userRepository = new UserRepository();

        // --- admin: resolve target employee + prepare the test account ------
        String adminPassword = loginAsAdmin(auth);
        Long targetId = employeeService.findAll(
                        new EmployeeRepository.Filter(TARGET_CODE, null, null, "ACTIVE"))
                .stream().filter(e -> TARGET_CODE.equals(e.getCode()))
                .map(Employee::getId).findFirst().orElseThrow();

        var employeeRole = userService.findRoles().stream()
                .filter(role -> role.code().equals("EMPLOYEE")).findFirst().orElseThrow();
        Long userId;
        var existing = userRepository.findAccountByEmail(SMOKE_EMAIL).orElse(null);
        if (existing == null) {
            userId = userService.createUser(SMOKE_USERNAME, "Self Scope Smoke",
                    SMOKE_EMAIL, FIRST_PASSWORD,
                    List.of(employeeRole.id()));
        } else {
            userId = existing.id();
            userService.setActive(userId, true);
            userService.updateRoles(userId, List.of(employeeRole.id()));
            userService.resetPassword(userId, FIRST_PASSWORD);
        }
        userService.setEmployeeLink(userId, targetId);
        check("admin can link an employee record",
                () -> targetId.equals(userRepository.findAccountById(userId)
                        .orElseThrow().employeeId()));

        // duplicate-owner guard
        check("linking one employee twice is refused", () -> {
            try {
                userService.setEmployeeLink(SessionContext.currentUserId(), targetId);
                return false;
            } catch (BusinessException e) {
                return true;
            }
        });

        // --- scoped sign-in -------------------------------------------------
        auth.logout();
        auth.login(SMOKE_EMAIL, FIRST_PASSWORD);
        auth.completeForcedPasswordChange(OWN_PASSWORD);
        auth.logout();
        auth.login(SMOKE_EMAIL, OWN_PASSWORD);

        Long scope = employeeService.selfScopeEmployeeId();
        check("scope resolves to the linked employee",
                () -> scope != null && scope == targetId);
        long otherId = scope == 1 ? 2 : 1;

        // listings contain own rows only
        boolean leaveOwnOnly = leaveService.findAll(null, null, null).stream()
                .allMatch(row -> row.getEmployeeId() == scope);
        check("leave listing shows own requests only", () -> leaveOwnOnly);

        boolean overtimeOwnOnly = overtimeService.findAll(null, null).stream()
                .allMatch(row -> row.getEmployeeId() == scope);
        check("overtime listing shows own requests only", () -> overtimeOwnOnly);

        boolean attendanceOwnOnly = attendanceService
                .findByDate(LocalDate.now(), null, null, null).stream()
                .allMatch(record -> record.getEmployeeId() == scope);
        check("attendance day view shows own records only", () -> attendanceOwnOnly);

        boolean performanceOwnOnly = ServiceRegistry.performanceService()
                .findAll(null, null).stream()
                .allMatch(review -> review.getEmployeeId() == scope);
        check("performance listing shows own reviews only", () -> performanceOwnOnly);

        boolean shiftsOwnOnly = ServiceRegistry.shiftService()
                .currentAssignments().stream()
                .allMatch(assignment -> assignment.getEmployeeId() == scope);
        check("shift assignments show own row only", () -> shiftsOwnOnly);

        check("another employee's shift history is refused", () -> {
            try {
                ServiceRegistry.shiftService().historyForEmployee(otherId);
                return false;
            } catch (RuntimeException e) {
                return true;
            }
        });

        // submission coercion: ask for someone else, land on yourself
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployeeId(otherId);
        request.setRequestDate(LocalDate.now());
        request.setHours(new BigDecimal("2"));
        request.setReason("scope smoke");
        long requestId = overtimeService.request(request);
        try {
            OvertimeRequest stored = new OvertimeRepository().findById(requestId)
                    .orElseThrow();
            check("request filed for another employee is coerced to self",
                    () -> stored.getEmployeeId() == scope);
        } finally {
            new com.ams.hrms.repository.Sql().executeUpdate(
                    "DELETE FROM overtime_requests WHERE id = ?", requestId);
        }

        // cross-reads are refused (scoped users lack directory rights or scope)
        check("reading another employee's record is refused", () -> {
            try {
                employeeService.findById(otherId);
                return false;
            } catch (RuntimeException e) {
                return true;
            }
        });
        check("reading another employee's payslips is refused", () -> {
            try {
                payrollService.findByEmployee(otherId);
                return false;
            } catch (RuntimeException e) {
                return true;
            }
        });
        check("reading another employee's balances is refused", () -> {
            try {
                leaveService.balances(otherId, LocalDate.now().getYear());
                return false;
            } catch (RuntimeException e) {
                return true;
            }
        });
        check("own profile stays readable", () -> {
            employeeService.findById(scope);
            payrollService.findByEmployee(scope);
            return true;
        });

        // --- cleanup ----------------------------------------------------------
        auth.logout();
        auth.login("admin@ams.local", adminPassword);
        userService.setEmployeeLink(userId, null);
        userService.setActive(userId, false);

        System.out.println("passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Signs in as admin, tolerating leftover passwords from earlier runs. */
    private static String loginAsAdmin(AuthService auth) throws Exception {
        for (String candidate : new String[] {"Admin@123"}) {
            try {
                auth.login("admin@ams.local", candidate);
                return candidate;
            } catch (Exception e) {
                // try the next candidate
            }
        }
        throw new IllegalStateException("Could not sign in as admin");
    }

    private static void check(String name, java.util.concurrent.Callable<Boolean> assertion) {
        try {
            boolean ok = Boolean.TRUE.equals(assertion.call());
            System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
            if (ok) {
                passed++;
            } else {
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] " + name + " (" + e.getMessage() + ")");
            failed++;
        }
    }
}

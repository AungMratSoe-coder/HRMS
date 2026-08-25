package com.ams.hrms.config;

import com.ams.hrms.repository.AuditRepository;
import com.ams.hrms.repository.AssetRepository;
import com.ams.hrms.repository.DashboardRepository;
import com.ams.hrms.repository.DepartmentRepository;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.NotificationRepository;
import com.ams.hrms.repository.OnboardingRepository;
import com.ams.hrms.repository.PerformanceRepository;
import com.ams.hrms.repository.PositionRepository;
import com.ams.hrms.repository.RecruitmentRepository;
import com.ams.hrms.repository.SeparationRepository;
import com.ams.hrms.repository.SettingsRepository;
import com.ams.hrms.repository.TrainingRepository;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.LoginAttemptGuard;
import com.ams.hrms.service.AssetService;
import com.ams.hrms.service.AuditService;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DashboardService;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.service.EmployeeService;
import com.ams.hrms.service.OnboardingService;
import com.ams.hrms.service.PerformanceService;
import com.ams.hrms.service.PositionService;
import com.ams.hrms.service.AttendanceService;
import com.ams.hrms.service.LeaveService;
import com.ams.hrms.service.NotificationService;
import com.ams.hrms.service.PayslipService;
import com.ams.hrms.service.PayrollService;
import com.ams.hrms.service.OvertimeService;
import com.ams.hrms.service.RecruitmentService;
import com.ams.hrms.service.SeparationService;
import com.ams.hrms.service.SettingsService;
import com.ams.hrms.service.ShiftService;
import com.ams.hrms.service.TrainingService;

/**
 * Minimal manual dependency-injection container (architecture decision #1).
 * Initialized once during bootstrap; UI and services resolve collaborators
 * from here instead of constructing their own graphs.
 */
public final class ServiceRegistry {

    private static volatile AuditService auditService;
    private static volatile AuthService authService;
    private static volatile DashboardService dashboardService;
    private static volatile DepartmentService departmentService;
    private static volatile PositionService positionService;
    private static volatile EmployeeService employeeService;
    private static volatile com.ams.hrms.service.DocumentService documentService;
    private static volatile ShiftService shiftService;
    private static volatile AttendanceService attendanceService;
    private static volatile LeaveService leaveService;
    private static volatile OvertimeService overtimeService;
    private static volatile PayrollService payrollService;
    private static volatile PayslipService payslipService;
    private static volatile RecruitmentService recruitmentService;
    private static volatile OnboardingService onboardingService;
    private static volatile PerformanceService performanceService;
    private static volatile TrainingService trainingService;
    private static volatile AssetService assetService;
    private static volatile SeparationService separationService;
    private static volatile com.ams.hrms.service.ReportService reportService;
    private static volatile NotificationService notificationService;
    private static volatile SettingsService settingsService;
    private static volatile com.ams.hrms.service.UserService userService;

    private ServiceRegistry() {
    }

    /** Wires all service singletons. Idempotent. */
    public static synchronized void initialize() {
        if (auditService != null) {
            return;
        }
        auditService = new AuditService(new AuditRepository());
        authService = new AuthService(new UserRepository(), auditService, new LoginAttemptGuard());
        dashboardService = new DashboardService(new DashboardRepository());
        departmentService = new DepartmentService(new DepartmentRepository(), auditService);
        positionService = new PositionService(new PositionRepository(), auditService);
        employeeService = new EmployeeService(
                new EmployeeRepository(), new PositionRepository(), auditService);
        documentService = new com.ams.hrms.service.DocumentService(
                new com.ams.hrms.repository.EmployeeDocumentRepository(),
                auditService, employeeService);
        leaveService = new LeaveService(new com.ams.hrms.repository.LeaveRepository(),
                auditService, employeeService);
        overtimeService = new OvertimeService(new com.ams.hrms.repository.OvertimeRepository(),
                auditService, employeeService);
        payrollService = new PayrollService(new com.ams.hrms.repository.PayrollRepository(),
                auditService, employeeService);
        payslipService = new PayslipService(new com.ams.hrms.repository.PayrollRepository());
        attendanceService = new AttendanceService(
                new com.ams.hrms.repository.AttendanceRepository(), auditService,
                employeeService);
        onboardingService = new OnboardingService(new OnboardingRepository(), auditService);
        performanceService = new PerformanceService(new PerformanceRepository(), auditService,
                employeeService);
        trainingService = new TrainingService(new TrainingRepository(), auditService);
        assetService = new AssetService(new AssetRepository(), auditService);
        separationService = new SeparationService(new SeparationRepository(),
                new com.ams.hrms.repository.EmployeeShiftRepository(),
                new AssetRepository(), auditService,
                new com.ams.hrms.repository.EmployeeRepository());
        recruitmentService = new RecruitmentService(new RecruitmentRepository(),
                auditService, employeeService, onboardingService);
        shiftService = new ShiftService(new com.ams.hrms.repository.ShiftRepository(),
                new com.ams.hrms.repository.EmployeeShiftRepository(),
                new EmployeeRepository(), auditService, employeeService);
        reportService = new com.ams.hrms.service.ReportService(
                new com.ams.hrms.repository.ReportRepository(), auditService);
        notificationService = new NotificationService(new NotificationRepository());
        settingsService = new SettingsService(new SettingsRepository(), auditService);
        userService = new com.ams.hrms.service.UserService(new UserRepository(), auditService,
                new EmployeeRepository());
        notificationService.registerDomainListeners();
    }

    public static AuditService auditService() {
        require(auditService, "AuditService");
        return auditService;
    }

    public static AuthService authService() {
        require(authService, "AuthService");
        return authService;
    }

    public static DashboardService dashboardService() {
        require(dashboardService, "DashboardService");
        return dashboardService;
    }

    public static DepartmentService departmentService() {
        require(departmentService, "DepartmentService");
        return departmentService;
    }

    public static PositionService positionService() {
        require(positionService, "PositionService");
        return positionService;
    }

    public static com.ams.hrms.service.DocumentService documentService() {
        require(documentService, "DocumentService");
        return documentService;
    }

    public static PayslipService payslipService() {
        require(payslipService, "PayslipService");
        return payslipService;
    }

    public static PayrollService payrollService() {
        require(payrollService, "PayrollService");
        return payrollService;
    }

    public static OvertimeService overtimeService() {
        require(overtimeService, "OvertimeService");
        return overtimeService;
    }

    public static LeaveService leaveService() {
        require(leaveService, "LeaveService");
        return leaveService;
    }

    public static AttendanceService attendanceService() {
        require(attendanceService, "AttendanceService");
        return attendanceService;
    }

    public static RecruitmentService recruitmentService() {
        require(recruitmentService, "RecruitmentService");
        return recruitmentService;
    }

    public static OnboardingService onboardingService() {
        require(onboardingService, "OnboardingService");
        return onboardingService;
    }

    public static PerformanceService performanceService() {
        require(performanceService, "PerformanceService");
        return performanceService;
    }

    public static TrainingService trainingService() {
        require(trainingService, "TrainingService");
        return trainingService;
    }

    public static AssetService assetService() {
        require(assetService, "AssetService");
        return assetService;
    }

    public static SeparationService separationService() {
        require(separationService, "SeparationService");
        return separationService;
    }

    public static com.ams.hrms.service.ReportService reportService() {
        require(reportService, "ReportService");
        return reportService;
    }

    public static NotificationService notificationService() {
        require(notificationService, "NotificationService");
        return notificationService;
    }

    public static SettingsService settingsService() {
        require(settingsService, "SettingsService");
        return settingsService;
    }

    public static com.ams.hrms.service.UserService userService() {
        require(userService, "UserService");
        return userService;
    }

    public static ShiftService shiftService() {
        require(shiftService, "ShiftService");
        return shiftService;
    }

    public static EmployeeService employeeService() {
        require(employeeService, "EmployeeService");
        return employeeService;
    }

    private static void require(Object instance, String name) {
        if (instance == null) {
            throw new IllegalStateException(name + " is not initialized; call ServiceRegistry.initialize() first");
        }
    }
}

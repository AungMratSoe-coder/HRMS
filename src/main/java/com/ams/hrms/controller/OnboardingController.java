package com.ams.hrms.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.OnboardingTask;
import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.service.OnboardingService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Onboarding module; all calls run off the EDT. */
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    public void loadTasks(long employeeId, Consumer<List<OnboardingTask>> onSuccess) {
        UiThread.executeAsync("Load onboarding tasks",
                () -> onboardingService.tasksForEmployee(employeeId), onSuccess);
    }

    public void loadTemplates(Consumer<List<OnboardingTemplate>> onSuccess) {
        UiThread.executeAsync("Load onboarding templates",
                () -> onboardingService.allTemplates(), onSuccess);
    }

    public void loadEmployees(Consumer<List<Employee>> onSuccess) {
        UiThread.executeAsync("Load employees",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)), onSuccess);
    }

    public void pendingEmployeeIds(Consumer<List<Long>> onSuccess) {
        UiThread.executeAsync("Load pending onboarding employees",
                () -> onboardingService.employeeIdsWithPendingTasks(), onSuccess);
    }

    public void hasChecklist(long employeeId, Consumer<Boolean> onSuccess) {
        UiThread.executeAsync("Check onboarding checklist",
                () -> onboardingService.hasChecklist(employeeId), onSuccess);
    }

    public void generateChecklist(long employeeId, LocalDate dueDate,
                                  Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Generate onboarding checklist",
                () -> {
                    onboardingService.generateChecklist(employeeId, dueDate);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setTaskStatus(long taskId, String status,
                              Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Update onboarding task",
                () -> {
                    onboardingService.setTaskStatus(taskId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void saveTemplate(OnboardingTemplate template,
                             Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Save onboarding template",
                () -> {
                    onboardingService.saveTemplate(template);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

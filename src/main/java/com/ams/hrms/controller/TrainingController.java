package com.ams.hrms.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.service.TrainingService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Training module; all calls run off the EDT. */
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public void loadPrograms(String keyword, String status,
                             Consumer<List<TrainingProgram>> onSuccess) {
        UiThread.executeAsync("Load training programs",
                () -> trainingService.findPrograms(keyword, status), onSuccess);
    }

    public void loadSessions(Long programId, String status,
                             Consumer<List<TrainingSession>> onSuccess) {
        UiThread.executeAsync("Load training sessions",
                () -> trainingService.findSessions(programId, status), onSuccess);
    }

    public void loadEnrollments(Long programId, String result, String keyword,
                                Consumer<List<EmployeeTraining>> onSuccess) {
        UiThread.executeAsync("Load enrollments",
                () -> trainingService.findEnrollments(programId, result, keyword),
                onSuccess);
    }

    public void loadEmployees(Consumer<List<Employee>> onSuccess) {
        UiThread.executeAsync("Load employees",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)), onSuccess);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public void saveProgram(TrainingProgram program, Runnable onDone,
                            Consumer<Exception> onError) {
        UiThread.executeAsync("Save program",
                () -> {
                    trainingService.saveProgram(program);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setProgramStatus(long programId, String status, Runnable onDone,
                                 Consumer<Exception> onError) {
        UiThread.executeAsync("Update program status",
                () -> {
                    trainingService.setProgramStatus(programId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void saveSession(TrainingSession session, Runnable onDone,
                            Consumer<Exception> onError) {
        UiThread.executeAsync("Save session",
                () -> {
                    trainingService.saveSession(session);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setSessionStatus(long sessionId, String status, Runnable onDone,
                                 Consumer<Exception> onError) {
        UiThread.executeAsync("Update session status",
                () -> {
                    trainingService.setSessionStatus(sessionId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void enroll(EmployeeTraining enrollment, Consumer<Long> onSuccess,
                       Consumer<Exception> onError) {
        UiThread.executeAsync("Enroll employee",
                () -> trainingService.enroll(enrollment), onSuccess, onError);
    }

    public void unenroll(long enrollmentId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Unenroll employee",
                () -> {
                    trainingService.unenroll(enrollmentId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void recordResult(long enrollmentId, String result, BigDecimal score,
                             LocalDate completionDate, String notes,
                             Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Record training result",
                () -> {
                    trainingService.recordResult(enrollmentId, result, score, notes);
                    return null;
                },
                done -> onDone.run(), onError);
    }
}

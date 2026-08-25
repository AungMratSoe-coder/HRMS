package com.ams.hrms.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Employee;
import com.ams.hrms.repository.EmployeeRepository.Filter;
import com.ams.hrms.repository.EmployeeRepository.HistoryEntry;
import com.ams.hrms.service.EmployeeService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Employees module; all calls run off the EDT. */
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    public void load(Filter filter, Consumer<List<Employee>> onSuccess) {
        UiThread.executeAsync("Load employees", () -> employeeService.findAll(filter), onSuccess);
    }

    /** Loads one page plus the matching total in a single background task. */
    public void loadPage(Filter filter, int page, int pageSize, Consumer<PageResult> onSuccess) {
        UiThread.executeAsync("Load employee page", () -> {
            long total = employeeService.countMatching(filter);
            int offset = Math.max(0, (page - 1) * pageSize);
            List<Employee> rows = employeeService.searchPage(filter, offset, pageSize);
            return new PageResult(rows, total);
        }, onSuccess);
    }

    public record PageResult(List<Employee> rows, long totalMatching) {
    }

    public void save(Employee employee, Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save employee", () -> employeeService.save(employee), onSuccess, onError);
    }

    public void uploadPhoto(long employeeId, Path sourceFile, Runnable onDone) {
        UiThread.executeAsync("Upload photo",
                () -> {
                    employeeService.uploadPhoto(employeeId, sourceFile);
                    return null;
                },
                result -> onDone.run());
    }

    public void setStatus(long id, String status, Runnable onDone) {
        UiThread.executeAsync("Update employee status",
                () -> {
                    employeeService.setStatus(id, status);
                    return null;
                },
                result -> onDone.run());
    }

    public void loadHistory(long employeeId, Consumer<List<HistoryEntry>> onSuccess) {
        UiThread.executeAsync("Load history",
                () -> employeeService.findHistory(employeeId), onSuccess);
    }
}

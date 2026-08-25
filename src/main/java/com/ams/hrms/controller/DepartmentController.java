package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Department;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.util.UiThread;

/** Thin view-controller for the Departments module (async service calls). */
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    public void load(String keyword, Consumer<List<Department>> onSuccess) {
        UiThread.executeAsync("Load departments", () -> departmentService.findAll(keyword), onSuccess);
    }

    /** Saves; errors are routed through the central error handler unless overridden. */
    public void save(Department department, Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save department",
                () -> departmentService.save(department), onSuccess, onError);
    }

    public void setStatus(long id, String status, Runnable onDone) {
        UiThread.executeAsync("Update department status",
                () -> {
                    departmentService.setStatus(id, status);
                    return null;
                },
                result -> onDone.run());
    }
}

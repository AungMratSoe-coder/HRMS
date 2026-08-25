package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.EmployeeLeaveRequest;
import com.ams.hrms.repository.LeaveRepository.BalanceRow;
import com.ams.hrms.repository.LeaveRepository.LeaveTypeOption;
import com.ams.hrms.service.LeaveService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Leave module; calls run off the EDT. */
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    public void loadRequests(String keyword, String status, Long typeId,
                             Consumer<List<EmployeeLeaveRequest>> onSuccess) {
        UiThread.executeAsync("Load leave requests",
                () -> leaveService.findAll(keyword, status, typeId), onSuccess);
    }

    public void loadTypes(Consumer<List<LeaveTypeOption>> onSuccess) {
        UiThread.executeAsync("Load leave types",
                () -> leaveService.activeTypes(), onSuccess);
    }

    public void loadBalances(long employeeId, int year, Consumer<List<BalanceRow>> onSuccess) {
        UiThread.executeAsync("Load balances",
                () -> leaveService.balances(employeeId, year), onSuccess);
    }

    public void availableDays(long employeeId, long typeId, int year,
                              Consumer<java.math.BigDecimal> onSuccess) {
        UiThread.executeAsync("Check balance",
                () -> leaveService.availableDays(employeeId, typeId, year), onSuccess);
    }

    public void submit(EmployeeLeaveRequest request, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Submit leave request",
                () -> {
                    leaveService.request(request);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void approve(long requestId, String level, String comments, Runnable onDone,
                        Consumer<Exception> onError) {
        UiThread.executeAsync("Approve leave",
                () -> {
                    leaveService.approve(requestId, level, comments);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void reject(long requestId, String reason, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Reject leave",
                () -> {
                    leaveService.reject(requestId, reason);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void cancel(long requestId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Cancel leave",
                () -> {
                    leaveService.cancel(requestId);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

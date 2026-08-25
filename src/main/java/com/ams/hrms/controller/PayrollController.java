package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.repository.PayrollRepository.PayrollRow;
import com.ams.hrms.repository.PayrollRepository.Period;
import com.ams.hrms.service.PayrollService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Payroll module; calls run off the EDT. */
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    public void loadPeriods(Consumer<List<Period>> onSuccess) {
        UiThread.executeAsync("Load periods", () -> payrollService.allPeriods(), onSuccess);
    }

    public void loadPayrolls(long periodId, Consumer<List<PayrollRow>> onSuccess) {
        UiThread.executeAsync("Load payrolls",
                () -> payrollService.findByPeriod(periodId), onSuccess);
    }

    public void calculate(int year, int month, java.util.function.IntConsumer onDone) {
        UiThread.executeAsync("Calculate payroll",
                () -> payrollService.calculate(year, month), onDone::accept);
    }

    public void transition(long payrollId, String status, Runnable onDone,
                           Consumer<Exception> onError) {
        UiThread.executeAsync("Transition " + status,
                () -> {
                    payrollService.transition(payrollId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void transitionBulk(long periodId, String fromStatus, String toStatus,
                               Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Bulk transition " + toStatus,
                () -> {
                    payrollService.transitionPeriod(periodId, fromStatus, toStatus);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

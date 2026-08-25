package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.service.OvertimeService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Overtime module; calls run off the EDT. */
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    public void load(String keyword, String status,
                     Consumer<List<OvertimeRequest>> onSuccess) {
        UiThread.executeAsync("Load overtime requests",
                () -> overtimeService.findAll(keyword, status), onSuccess);
    }

    public void submit(OvertimeRequest request, Consumer<Long> onSuccess,
                       Consumer<Exception> onError) {
        UiThread.executeAsync("Submit overtime request",
                () -> overtimeService.request(request), onSuccess, onError);
    }

    public void approve(long requestId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Approve overtime",
                () -> {
                    overtimeService.approve(requestId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void reject(long requestId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Reject overtime",
                () -> {
                    overtimeService.reject(requestId);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

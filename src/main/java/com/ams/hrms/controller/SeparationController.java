package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Resignation;
import com.ams.hrms.model.Termination;
import com.ams.hrms.service.SeparationService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Separation module; all calls run off the EDT. */
public class SeparationController {

    private final SeparationService separationService;

    public SeparationController(SeparationService separationService) {
        this.separationService = separationService;
    }

    public void loadResignations(String keyword, String status,
                                 Consumer<List<Resignation>> onSuccess) {
        UiThread.executeAsync("Load resignations",
                () -> separationService.findResignations(keyword, status), onSuccess);
    }

    public void loadTerminations(String keyword, Consumer<List<Termination>> onSuccess) {
        UiThread.executeAsync("Load terminations",
                () -> separationService.findTerminations(keyword), onSuccess);
    }

    public void recordResignation(Resignation resignation, Consumer<Long> onSuccess,
                                  Consumer<Exception> onError) {
        UiThread.executeAsync("Record resignation",
                () -> separationService.recordResignation(resignation), onSuccess, onError);
    }

    public void approveResignation(long id, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Approve resignation",
                () -> {
                    separationService.approveResignation(id);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void closeResignation(long id, String targetStatus, Runnable onDone,
                                 Consumer<Exception> onError) {
        UiThread.executeAsync("Close resignation",
                () -> {
                    separationService.closeResignation(id, targetStatus);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void processResignation(long id, Consumer<String> onSuccess,
                                   Consumer<Exception> onError) {
        UiThread.executeAsync("Process resignation exit checklist",
                () -> separationService.processResignation(id), onSuccess, onError);
    }

    public void saveExitInterviewNotes(long id, String notes, Runnable onDone,
                                       Consumer<Exception> onError) {
        UiThread.executeAsync("Save exit interview notes",
                () -> {
                    separationService.saveExitInterviewNotes(id, notes);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void terminate(Termination termination, Consumer<String> onSuccess,
                          Consumer<Exception> onError) {
        UiThread.executeAsync("Record termination",
                () -> separationService.terminate(termination), onSuccess, onError);
    }
}

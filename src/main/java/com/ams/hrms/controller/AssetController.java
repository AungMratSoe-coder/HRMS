package com.ams.hrms.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Asset;
import com.ams.hrms.model.AssetAssignment;
import com.ams.hrms.service.AssetService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Asset module; all calls run off the EDT. */
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    public void loadAssets(String keyword, String category, String status,
                           Consumer<List<Asset>> onSuccess) {
        UiThread.executeAsync("Load assets",
                () -> assetService.findAssets(keyword, category, status), onSuccess);
    }

    public void loadAssignments(Long assetId, Long employeeId, String status, String keyword,
                                Consumer<List<AssetAssignment>> onSuccess) {
        UiThread.executeAsync("Load asset assignments",
                () -> assetService.findAssignments(assetId, employeeId, status, keyword),
                onSuccess);
    }

    public void saveAsset(Asset asset, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Save asset",
                () -> {
                    assetService.saveAsset(asset);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setAssetStatus(long assetId, String status, Runnable onDone,
                               Consumer<Exception> onError) {
        UiThread.executeAsync("Update asset status",
                () -> {
                    assetService.setAssetStatus(assetId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void assign(long assetId, long employeeId, LocalDate assignedDate,
                       LocalDate dueReturnDate, String notes,
                       Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Assign asset",
                () -> assetService.assign(assetId, employeeId, assignedDate,
                        dueReturnDate, notes),
                onSuccess, onError);
    }

    public void returnAsset(long assignmentId, LocalDate returnedDate,
                            String conditionOnReturn, String notes,
                            Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Return asset",
                () -> {
                    assetService.returnAsset(assignmentId, returnedDate,
                            conditionOnReturn, notes);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void markLost(long assignmentId, String notes, Runnable onDone,
                         Consumer<Exception> onError) {
        UiThread.executeAsync("Mark asset lost",
                () -> {
                    assetService.markLost(assignmentId, notes);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

package com.ams.hrms.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ams.hrms.model.AppSetting;
import com.ams.hrms.service.BackupService;
import com.ams.hrms.service.SettingsService;
import com.ams.hrms.util.UiThread;

/** View-controller for application settings; calls run off the EDT. */
public class SettingsController {

    private final SettingsService settingsService;
    private final BackupService backupService;

    public SettingsController(SettingsService settingsService) {
        this(settingsService, null);
    }

    public SettingsController(SettingsService settingsService, BackupService backupService) {
        this.settingsService = settingsService;
        this.backupService = backupService;
    }

    public void loadSettings(Consumer<List<AppSetting>> onSuccess) {
        UiThread.executeAsync("Load settings", () -> settingsService.findAll(), onSuccess);
    }

    public void saveChanges(Map<String, String> valuesByKey,
                            Consumer<Integer> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save settings",
                () -> settingsService.saveAll(valuesByKey), onSuccess, onError);
    }

    public void backupTo(Path targetFile, Consumer<Path> onSuccess,
                         Consumer<Exception> onError) {
        UiThread.executeAsync("Backup database",
                () -> requireBackup().backupTo(targetFile), onSuccess, onError);
    }

    public void restoreFrom(Path dumpFile, Runnable onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Restore database",
                () -> {
                    requireBackup().restoreFrom(dumpFile);
                    return true;
                }, ok -> onSuccess.run(), onError);
    }

    private BackupService requireBackup() {
        if (backupService == null) {
            throw new IllegalStateException("BackupService was not wired into this controller");
        }
        return backupService;
    }
}

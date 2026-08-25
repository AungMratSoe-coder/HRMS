package com.ams.hrms.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ams.hrms.model.AppSetting;
import com.ams.hrms.service.SettingsService;
import com.ams.hrms.util.UiThread;

/** View-controller for application settings; calls run off the EDT. */
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void loadSettings(Consumer<List<AppSetting>> onSuccess) {
        UiThread.executeAsync("Load settings", () -> settingsService.findAll(), onSuccess);
    }

    public void saveChanges(Map<String, String> valuesByKey,
                            Consumer<Integer> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save settings",
                () -> settingsService.saveAll(valuesByKey), onSuccess, onError);
    }
}

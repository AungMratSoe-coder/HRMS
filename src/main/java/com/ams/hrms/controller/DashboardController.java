package com.ams.hrms.controller;

import java.util.function.Consumer;

import com.ams.hrms.dto.DashboardData;
import com.ams.hrms.service.DashboardService;
import com.ams.hrms.util.UiThread;

/**
 * Dashboard view-controller: loads data off the EDT and delivers the result
 * back for rendering. Keeps the panel free of threading and service wiring.
 */
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Loads all dashboard data; {@code onSuccess} runs on the EDT. */
    public void load(Consumer<DashboardData> onSuccess) {
        UiThread.executeAsync("Load dashboard", dashboardService::load, onSuccess);
    }
}

package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Position;
import com.ams.hrms.service.PositionService;
import com.ams.hrms.util.UiThread;

/** Thin view-controller for the Positions module (async service calls). */
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    public void load(String keyword, Consumer<List<Position>> onSuccess) {
        UiThread.executeAsync("Load positions", () -> positionService.findAll(keyword), onSuccess);
    }

    public void save(Position position, Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save position",
                () -> positionService.save(position), onSuccess, onError);
    }

    public void setStatus(long id, String status, Runnable onDone) {
        UiThread.executeAsync("Update position status",
                () -> {
                    positionService.setStatus(id, status);
                    return null;
                },
                result -> onDone.run());
    }
}

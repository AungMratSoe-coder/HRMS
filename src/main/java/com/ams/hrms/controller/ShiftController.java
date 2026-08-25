package com.ams.hrms.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.EmployeeShift;
import com.ams.hrms.model.Shift;
import com.ams.hrms.service.ShiftService;
import com.ams.hrms.util.UiThread;

/** View-controller for shifts and their assignments; calls run off the EDT. */
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    public void loadShifts(String keyword, Consumer<List<Shift>> onSuccess) {
        UiThread.executeAsync("Load shifts", () -> shiftService.findAll(keyword), onSuccess);
    }

    public void countOpenAssignments(long shiftId, java.util.function.LongConsumer onSuccess) {
        UiThread.executeAsync("Count assignments",
                () -> shiftService.countOpenAssignments(shiftId), onSuccess::accept);
    }

    public void saveShift(Shift shift, Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Save shift", () -> shiftService.save(shift), onSuccess, onError);
    }

    public void setShiftStatus(long id, String status, Runnable onDone) {
        UiThread.executeAsync("Update shift status",
                () -> {
                    shiftService.setStatus(id, status);
                    return null;
                },
                result -> onDone.run());
    }

    public void loadAssignments(Consumer<List<EmployeeShift>> onSuccess) {
        UiThread.executeAsync("Load assignments", () -> shiftService.currentAssignments(), onSuccess);
    }

    public void assign(long employeeId, long shiftId, LocalDate from,
                       Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Assign shift",
                () -> {
                    shiftService.assign(employeeId, shiftId, from);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void endAssignment(long assignmentId, LocalDate end,
                              Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("End assignment",
                () -> {
                    shiftService.endAssignment(assignmentId, end);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void loadHistory(long employeeId, Consumer<List<EmployeeShift>> onSuccess) {
        UiThread.executeAsync("Load assignment history",
                () -> shiftService.historyForEmployee(employeeId), onSuccess);
    }
}

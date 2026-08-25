package com.ams.hrms.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.repository.AttendanceRepository.MonthSummary;
import com.ams.hrms.service.AttendanceService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Attendance module; calls run off the EDT. */
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public void loadDay(LocalDate date, String keyword, Long departmentId, String status,
                        Consumer<List<AttendanceRecord>> onSuccess) {
        UiThread.executeAsync("Load attendance",
                () -> attendanceService.findByDate(date, keyword, departmentId, status),
                onSuccess);
    }

    public void checkIn(long employeeId, Runnable onDone) {
        UiThread.executeAsync("Check in",
                () -> {
                    attendanceService.checkIn(employeeId, java.time.LocalDateTime.now());
                    return null;
                },
                result -> onDone.run());
    }

    public void checkOut(long employeeId, Runnable onDone) {
        UiThread.executeAsync("Check out",
                () -> {
                    attendanceService.checkOut(employeeId, java.time.LocalDateTime.now());
                    return null;
                },
                result -> onDone.run());
    }

    public void correct(long recordId, java.time.LocalTime in, java.time.LocalTime out,
                        String reason, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Correct attendance",
                () -> {
                    attendanceService.correct(recordId, in, out, reason);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void generateDaily(LocalDate date, java.util.function.IntConsumer onDone) {
        UiThread.executeAsync("Generate daily attendance",
                () -> attendanceService.generateDaily(date), onDone::accept);
    }

    public void loadMonth(long employeeId, int year, int month,
                          Consumer<List<AttendanceRecord>> onSuccess,
                          Consumer<MonthSummary> onSummary) {
        UiThread.executeAsync("Load monthly attendance",
                () -> new Object[]{
                        attendanceService.findByEmployeeBetween(employeeId,
                                LocalDate.of(year, month, 1),
                                LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)),
                        attendanceService.monthTotals(employeeId, year, month)},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    List<AttendanceRecord> records = (List<AttendanceRecord>) parts[0];
                    onSuccess.accept(records);
                    onSummary.accept((MonthSummary) parts[1]);
                });
    }
}

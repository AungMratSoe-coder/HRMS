package com.ams.hrms.controller;

import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.service.UserService;
import com.ams.hrms.util.UiThread;

/** View-controller for user account administration; runs off the EDT. */
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public void loadUsers(Consumer<List<UserRepository.UserRow>> onSuccess) {
        UiThread.executeAsync("Load users", () -> userService.findAll(), onSuccess);
    }

    public void loadRoles(Consumer<List<UserRepository.RoleRef>> onSuccess) {
        UiThread.executeAsync("Load roles", () -> userService.findRoles(), onSuccess);
    }

    public void loadRoleIds(long userId, Consumer<List<Long>> onSuccess) {
        UiThread.executeAsync("Load user roles", () -> userService.findRoleIds(userId), onSuccess);
    }

    public void createUser(String username, String fullName, String email, String password,
                           List<Long> roleIds, Consumer<Long> onSuccess,
                           Consumer<Exception> onError) {
        UiThread.executeAsync("Create user",
                () -> userService.createUser(username, fullName, email, password, roleIds),
                onSuccess, onError);
    }

    public void resetPassword(long userId, String newPassword, Runnable onDone,
                              Consumer<Exception> onError) {
        UiThread.executeAsync("Reset password",
                () -> {
                    userService.resetPassword(userId, newPassword);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setActive(long userId, boolean active, Runnable onDone,
                          Consumer<Exception> onError) {
        UiThread.executeAsync("Update account state",
                () -> {
                    userService.setActive(userId, active);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void updateRoles(long userId, List<Long> roleIds, Runnable onDone,
                            Consumer<Exception> onError) {
        UiThread.executeAsync("Update roles",
                () -> {
                    userService.updateRoles(userId, roleIds);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void loadEmployeeOptions(
            Consumer<List<com.ams.hrms.repository.EmployeeRepository.EmployeeOption>> onSuccess) {
        UiThread.executeAsync("Load employee options", () -> userService.findEmployeeOptions(),
                onSuccess);
    }

    public void setEmployeeLink(long userId, Long employeeId, Runnable onDone,
                                Consumer<Exception> onError) {
        UiThread.executeAsync("Link employee record",
                () -> {
                    userService.setEmployeeLink(userId, employeeId);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}

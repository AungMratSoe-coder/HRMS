package com.ams.hrms.dto;

import java.util.List;

import com.ams.hrms.model.OnboardingTask;

/**
 * Aggregated onboarding completion state (spec section 15). Percentage counts
 * a task as settled when COMPLETED or WAIVED; SKIPPED still blocks the
 * mandatory-completion check so nothing quietly falls through.
 */
public record OnboardingProgress(
        long total,
        long completed,
        long pending,
        long skipped,
        long waived,
        long mandatoryOutstanding) {

    public static OnboardingProgress from(List<OnboardingTask> tasks) {
        long total = tasks.size();
        long completed = tasks.stream()
                .filter(task -> OnboardingTask.STATUS_COMPLETED.equals(task.getStatus()))
                .count();
        long skipped = tasks.stream()
                .filter(task -> OnboardingTask.STATUS_SKIPPED.equals(task.getStatus()))
                .count();
        long waived = tasks.stream()
                .filter(task -> OnboardingTask.STATUS_WAIVED.equals(task.getStatus()))
                .count();
        long pending = tasks.stream()
                .filter(task -> OnboardingTask.STATUS_PENDING.equals(task.getStatus()))
                .count();
        long mandatoryOutstanding = tasks.stream()
                .filter(task -> task.isMandatory()
                        && !OnboardingTask.STATUS_COMPLETED.equals(task.getStatus())
                        && !OnboardingTask.STATUS_WAIVED.equals(task.getStatus()))
                .count();
        return new OnboardingProgress(total, completed, pending, skipped, waived,
                mandatoryOutstanding);
    }

    /** 0-100; an empty checklist is trivially complete. */
    public int percentComplete() {
        if (total == 0) {
            return 100;
        }
        long settled = completed + waived;
        return (int) Math.round(settled * 100.0 / total);
    }

    /** True when every mandatory task is completed or explicitly waived. */
    public boolean isComplete() {
        return total > 0 && mandatoryOutstanding == 0;
    }
}

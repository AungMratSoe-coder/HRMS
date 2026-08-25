package com.ams.hrms.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ams.hrms.model.OnboardingTask;

/**
 * Progress math (spec sections 15 and 55): completion percentage and the
 * mandatory-completion rule are pure logic, verified without UI or database.
 */
class OnboardingProgressTest {

    private static OnboardingTask task(String status, boolean mandatory) {
        OnboardingTask task = new OnboardingTask();
        task.setStatus(status);
        task.setMandatory(mandatory);
        return task;
    }

    @Test
    void emptyChecklistIsTriviallyComplete() {
        OnboardingProgress progress = OnboardingProgress.from(List.of());
        assertThat(progress.total()).isZero();
        assertThat(progress.percentComplete()).isEqualTo(100);
        assertThat(progress.isComplete()).isFalse();
    }

    @Test
    void allPendingIsZeroPercent() {
        OnboardingProgress progress = OnboardingProgress.from(List.of(
                task("PENDING", true), task("PENDING", false), task("PENDING", true)));
        assertThat(progress.percentComplete()).isZero();
        assertThat(progress.pending()).isEqualTo(3);
        assertThat(progress.isComplete()).isFalse();
        assertThat(progress.mandatoryOutstanding()).isEqualTo(2);
    }

    @Test
    void completedCountFeedsPercentage() {
        OnboardingProgress progress = OnboardingProgress.from(List.of(
                task("COMPLETED", true), task("COMPLETED", false),
                task("PENDING", true), task("PENDING", true)));
        assertThat(progress.completed()).isEqualTo(2);
        assertThat(progress.percentComplete()).isEqualTo(50);
        assertThat(progress.mandatoryOutstanding()).isEqualTo(2);
        assertThat(progress.isComplete()).isFalse();
    }

    @Test
    void waivedTasksSettleButSkippedDoNot() {
        OnboardingProgress progress = OnboardingProgress.from(List.of(
                task("COMPLETED", true),
                task("WAIVED", true),
                task("SKIPPED", true)));
        assertThat(progress.percentComplete())
                .isEqualTo((int) Math.round(2 * 100.0 / 3));
        assertThat(progress.mandatoryOutstanding()).isEqualTo(1);
        assertThat(progress.isComplete()).isFalse();

        OnboardingProgress fullyWaived = OnboardingProgress.from(List.of(
                task("WAIVED", true), task("COMPLETED", true)));
        assertThat(fullyWaived.isComplete()).isTrue();
    }

    @Test
    void roundingUsesHalfUp() {
        OnboardingProgress oneOfThree = OnboardingProgress.from(List.of(
                task("COMPLETED", true), task("PENDING", true), task("PENDING", true)));
        assertThat(oneOfThree.percentComplete()).isEqualTo(33);
    }

    @Test
    void nonMandatoryTasksNeverBlockCompletion() {
        OnboardingProgress progress = OnboardingProgress.from(List.of(
                task("COMPLETED", true),
                task("SKIPPED", false)));
        assertThat(progress.isComplete()).isTrue();
        assertThat(progress.skipped()).isEqualTo(1);
    }
}

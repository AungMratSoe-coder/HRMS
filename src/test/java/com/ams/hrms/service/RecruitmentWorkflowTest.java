package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Workflow transition rules (spec sections 14 and 55): the recruitment
 * pipeline can only move forward along legal paths.
 */
class RecruitmentWorkflowTest {

    // ------------------------------------------------------------------
    // Applications
    // ------------------------------------------------------------------

    @Test
    void applicationHappyPathIsLegal() {
        assertThat(RecruitmentWorkflow.canTransitionApplication("SUBMITTED", "SCREENING")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionApplication("SCREENING", "INTERVIEW")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionApplication("INTERVIEW", "OFFER")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionApplication("OFFER", "ACCEPTED")).isTrue();
    }

    @Test
    void applicationCannotSkipStages() {
        assertThat(RecruitmentWorkflow.canTransitionApplication("SUBMITTED", "INTERVIEW")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionApplication("SUBMITTED", "OFFER")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionApplication("SCREENING", "ACCEPTED")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionApplication("INTERVIEW", "ACCEPTED")).isFalse();
    }

    @Test
    void applicationTerminalStatesAreFrozen() {
        for (String terminal : new String[]{"ACCEPTED", "REJECTED", "WITHDRAWN"}) {
            assertThat(RecruitmentWorkflow.applicationActive(terminal)).isFalse();
            assertThat(RecruitmentWorkflow.canTransitionApplication(terminal, "SCREENING"))
                    .isFalse();
        }
    }

    @Test
    void applicationCanBeRejectedOrWithdrawnWhileActive() {
        for (String active : new String[]{"SUBMITTED", "SCREENING", "INTERVIEW", "OFFER"}) {
            assertThat(RecruitmentWorkflow.canTransitionApplication(active, "REJECTED")).isTrue();
            assertThat(RecruitmentWorkflow.canTransitionApplication(active, "WITHDRAWN")).isTrue();
            assertThat(RecruitmentWorkflow.applicationActive(active)).isTrue();
        }
    }

    @Test
    void unknownApplicationStatusIsRejected() {
        assertThat(RecruitmentWorkflow.canTransitionApplication(null, "SCREENING")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionApplication("UNKNOWN", "SCREENING")).isFalse();
    }

    // ------------------------------------------------------------------
    // Candidates
    // ------------------------------------------------------------------

    @Test
    void candidateHappyPathIsLegal() {
        assertThat(RecruitmentWorkflow.canTransitionCandidate("NEW", "SHORTLISTED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionCandidate("SHORTLISTED", "INTERVIEWING"))
                .isTrue();
        assertThat(RecruitmentWorkflow.canTransitionCandidate("INTERVIEWING", "OFFERED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionCandidate("OFFERED", "HIRED")).isTrue();
    }

    @Test
    void candidateCannotSkipToHired() {
        assertThat(RecruitmentWorkflow.canTransitionCandidate("NEW", "HIRED")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionCandidate("INTERVIEWING", "HIRED")).isFalse();
    }

    @Test
    void hiredCandidateIsInactiveAndFrozen() {
        assertThat(RecruitmentWorkflow.candidateActive("HIRED")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionCandidate("HIRED", "REJECTED")).isFalse();
    }

    @Test
    void rejectedOrWithdrawnCandidateCanBeReopenedToNew() {
        for (String closed : new String[]{"REJECTED", "WITHDRAWN"}) {
            assertThat(RecruitmentWorkflow.canTransitionCandidate(closed, "NEW")).isTrue();
        }
    }

    @Test
    void hiredCandidateCannotBeReopened() {
        assertThat(RecruitmentWorkflow.canTransitionCandidate("HIRED", "NEW")).isFalse();
    }

    // ------------------------------------------------------------------
    // Offers
    // ------------------------------------------------------------------

    @Test
    void offerMustBeSentBeforeDecision() {
        assertThat(RecruitmentWorkflow.canTransitionOffer("DRAFT", "SENT")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionOffer("DRAFT", "ACCEPTED")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionOffer("SENT", "ACCEPTED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionOffer("SENT", "DECLINED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionOffer("SENT", "EXPIRED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionOffer("SENT", "CANCELLED")).isTrue();
    }

    @Test
    void draftOfferCanOnlyBeSentOrCancelled() {
        assertThat(RecruitmentWorkflow.canTransitionOffer("DRAFT", "CANCELLED")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionOffer("DRAFT", "DECLINED")).isFalse();
        assertThat(RecruitmentWorkflow.canTransitionOffer("DRAFT", "EXPIRED")).isFalse();
    }

    @Test
    void decidedOffersAreFrozen() {
        for (String terminal : new String[]{"ACCEPTED", "DECLINED", "EXPIRED", "CANCELLED"}) {
            assertThat(RecruitmentWorkflow.canTransitionOffer(terminal, "SENT")).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Vacancies
    // ------------------------------------------------------------------

    @Test
    void vacancyHoldAndReopenCycle() {
        assertThat(RecruitmentWorkflow.canTransitionVacancy("OPEN", "ON_HOLD")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionVacancy("ON_HOLD", "OPEN")).isTrue();
        assertThat(RecruitmentWorkflow.canTransitionVacancy("ON_HOLD", "ON_HOLD")).isFalse();
    }

    @Test
    void closedVacanciesNeverReopen() {
        for (String terminal : new String[]{"FILLED", "CLOSED", "CANCELLED"}) {
            assertThat(RecruitmentWorkflow.canTransitionVacancy(terminal, "OPEN")).isFalse();
        }
    }
}

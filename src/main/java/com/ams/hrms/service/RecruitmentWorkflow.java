package com.ams.hrms.service;

import java.util.Map;
import java.util.Set;

/**
 * Recruitment state machine (spec section 14). Pure transition logic with no
 * database or UI dependencies so the business rules stay unit-testable.
 *
 * <pre>
 * Vacancy:      OPEN -&gt; ON_HOLD -&gt; OPEN, any active -&gt; FILLED/CLOSED/CANCELLED
 * Candidate:    NEW -&gt; SHORTLISTED -&gt; INTERVIEWING -&gt; OFFERED -&gt; HIRED
 * Application:  SUBMITTED -&gt; SCREENING -&gt; INTERVIEW -&gt; OFFER -&gt; ACCEPTED
 * Offer:        DRAFT -&gt; SENT -&gt; ACCEPTED / DECLINED / EXPIRED / CANCELLED
 * </pre>
 */
public final class RecruitmentWorkflow {

    public static final Set<String> VACANCY_STATUSES =
            Set.of("OPEN", "ON_HOLD", "FILLED", "CLOSED", "CANCELLED");
    public static final Set<String> CANDIDATE_STATUSES =
            Set.of("NEW", "SHORTLISTED", "INTERVIEWING", "OFFERED", "HIRED",
                    "REJECTED", "WITHDRAWN");
    public static final Set<String> APPLICATION_STATUSES =
            Set.of("SUBMITTED", "SCREENING", "INTERVIEW", "OFFER",
                    "ACCEPTED", "REJECTED", "WITHDRAWN");
    public static final Set<String> OFFER_STATUSES =
            Set.of("DRAFT", "SENT", "ACCEPTED", "DECLINED", "EXPIRED", "CANCELLED");

    private static final Map<String, Set<String>> VACANCY_TRANSITIONS = Map.of(
            "OPEN", Set.of("ON_HOLD", "FILLED", "CLOSED", "CANCELLED"),
            "ON_HOLD", Set.of("OPEN", "CLOSED", "CANCELLED"),
            "FILLED", Set.of(),
            "CLOSED", Set.of(),
            "CANCELLED", Set.of());

    private static final Map<String, Set<String>> CANDIDATE_TRANSITIONS = Map.of(
            "NEW", Set.of("SHORTLISTED", "REJECTED", "WITHDRAWN"),
            "SHORTLISTED", Set.of("INTERVIEWING", "REJECTED", "WITHDRAWN"),
            "INTERVIEWING", Set.of("OFFERED", "REJECTED", "WITHDRAWN"),
            "OFFERED", Set.of("HIRED", "REJECTED", "WITHDRAWN"),
            "HIRED", Set.of(),
            "REJECTED", Set.of(),
            "WITHDRAWN", Set.of());

    private static final Map<String, Set<String>> APPLICATION_TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("SCREENING", "REJECTED", "WITHDRAWN"),
            "SCREENING", Set.of("INTERVIEW", "REJECTED", "WITHDRAWN"),
            "INTERVIEW", Set.of("OFFER", "REJECTED", "WITHDRAWN"),
            "OFFER", Set.of("ACCEPTED", "REJECTED", "WITHDRAWN"),
            "ACCEPTED", Set.of(),
            "REJECTED", Set.of(),
            "WITHDRAWN", Set.of());

    private static final Map<String, Set<String>> OFFER_TRANSITIONS = Map.of(
            "DRAFT", Set.of("SENT", "CANCELLED"),
            "SENT", Set.of("ACCEPTED", "DECLINED", "EXPIRED", "CANCELLED"),
            "ACCEPTED", Set.of(),
            "DECLINED", Set.of(),
            "EXPIRED", Set.of(),
            "CANCELLED", Set.of());

    private RecruitmentWorkflow() {
    }

    /** True when {@code from -> to} is a legal vacancy transition. */
    public static boolean canTransitionVacancy(String from, String to) {
        return allowed(VACANCY_TRANSITIONS, from, to);
    }

    /** True when {@code from -> to} is a legal candidate transition. */
    public static boolean canTransitionCandidate(String from, String to) {
        return allowed(CANDIDATE_TRANSITIONS, from, to);
    }

    /** True when {@code from -> to} is a legal application transition. */
    public static boolean canTransitionApplication(String from, String to) {
        return allowed(APPLICATION_TRANSITIONS, from, to);
    }

    /** True when {@code from -> to} is a legal offer transition. */
    public static boolean canTransitionOffer(String from, String to) {
        return allowed(OFFER_TRANSITIONS, from, to);
    }

    /** True while the application can still be worked on. */
    public static boolean applicationActive(String status) {
        return APPLICATION_TRANSITIONS.getOrDefault(status, Set.of()).isEmpty() == false;
    }

    /** True while the candidate is still moving through the pipeline. */
    public static boolean candidateActive(String status) {
        return !"HIRED".equals(status) && !"REJECTED".equals(status)
                && !"WITHDRAWN".equals(status);
    }

    private static boolean allowed(Map<String, Set<String>> table, String from, String to) {
        if (from == null || to == null || !table.containsKey(from)) {
            return false;
        }
        return table.get(from).contains(to);
    }
}

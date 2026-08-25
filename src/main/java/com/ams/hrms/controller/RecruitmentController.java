package com.ams.hrms.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.service.RecruitmentService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Recruitment module; all calls run off the EDT. */
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    public RecruitmentController(RecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public void loadVacancies(String keyword, String status,
                              Consumer<List<JobVacancy>> onSuccess) {
        UiThread.executeAsync("Load vacancies",
                () -> recruitmentService.findVacancies(keyword, status), onSuccess);
    }

    public void loadCandidates(String keyword, String status,
                               Consumer<List<Candidate>> onSuccess) {
        UiThread.executeAsync("Load candidates",
                () -> recruitmentService.findCandidates(keyword, status), onSuccess);
    }

    public void loadApplications(String keyword, String status, Long vacancyId,
                                 Consumer<List<JobApplication>> onSuccess) {
        UiThread.executeAsync("Load applications",
                () -> recruitmentService.findApplications(keyword, status, vacancyId),
                onSuccess);
    }

    public void loadInterviews(String keyword, String result,
                               Consumer<List<Interview>> onSuccess) {
        UiThread.executeAsync("Load interviews",
                () -> recruitmentService.findInterviews(keyword, result), onSuccess);
    }

    public void loadOffers(String keyword, String status,
                           Consumer<List<JobOffer>> onSuccess) {
        UiThread.executeAsync("Load offers",
                () -> recruitmentService.findOffers(keyword, status), onSuccess);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public void saveVacancy(JobVacancy vacancy, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Save vacancy",
                () -> {
                    recruitmentService.saveVacancy(vacancy);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setVacancyStatus(long vacancyId, String status,
                                 Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Update vacancy status",
                () -> {
                    recruitmentService.setVacancyStatus(vacancyId, status);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void saveCandidate(Candidate candidate, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Save candidate",
                () -> {
                    recruitmentService.saveCandidate(candidate);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void exitCandidate(long candidateId, String exitStatus, String reason,
                              Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Update candidate status",
                () -> {
                    recruitmentService.exitCandidate(candidateId, exitStatus, reason);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void apply(long candidateId, long vacancyId, String coverLetter,
                      Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Submit application",
                () -> {
                    recruitmentService.apply(candidateId, vacancyId, coverLetter);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void shortlist(long applicationId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Shortlist application",
                () -> {
                    recruitmentService.shortlist(applicationId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void rejectApplication(long applicationId, String reason,
                                  Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Reject application",
                () -> {
                    recruitmentService.rejectApplication(applicationId, reason);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void withdrawApplication(long applicationId,
                                    Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Withdraw application",
                () -> {
                    recruitmentService.withdrawApplication(applicationId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void scheduleInterview(Interview interview, Runnable onDone,
                                  Consumer<Exception> onError) {
        UiThread.executeAsync("Schedule interview",
                () -> {
                    recruitmentService.scheduleInterview(interview);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void recordInterviewResult(long interviewId, String result, BigDecimal score,
                                      String notes, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Record interview result",
                () -> {
                    recruitmentService.recordResult(interviewId, result, score, notes);
                    return null;
                },
                done -> onDone.run(), onError);
    }

    public void createOffer(JobOffer offer, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Create offer",
                () -> {
                    recruitmentService.createOffer(offer);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void sendOffer(long offerId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Send offer",
                () -> {
                    recruitmentService.sendOffer(offerId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void acceptOffer(long offerId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Accept offer",
                () -> {
                    recruitmentService.acceptOffer(offerId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void closeOffer(long offerId, String targetStatus,
                           Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Close offer",
                () -> {
                    recruitmentService.closeOffer(offerId, targetStatus);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void hire(long offerId, LocalDate joinDate,
                     Consumer<Long> onSuccess, Consumer<Exception> onError) {
        UiThread.executeAsync("Hire candidate",
                () -> recruitmentService.hire(offerId, joinDate), onSuccess, onError);
    }
}

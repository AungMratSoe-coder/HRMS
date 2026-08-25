package com.ams.hrms.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.repository.RecruitmentRepository;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.util.FileStorage;
import com.ams.hrms.validator.Validators;

/**
 * Recruitment business rules (spec section 14): vacancy and candidate CRUD,
 * application pipeline (SUBMITTED &rarr; SCREENING &rarr; INTERVIEW &rarr;
 * OFFER &rarr; ACCEPTED), interview rounds, offer lifecycle and the
 * transactional hire that converts a candidate into an employee. All state
 * changes flow through {@link RecruitmentWorkflow}; every operation is
 * RBAC-gated and audited.
 */
public class RecruitmentService {

    public static final String DATA_SCOPE = "recruitment";

    private static final Logger LOG = LoggerFactory.getLogger(RecruitmentService.class);

    private static final Set<String> EMPLOYMENT_TYPES =
            Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN", "PROBATION");
    private static final Set<String> SOURCES =
            Set.of("WEBSITE", "REFERRAL", "AGENCY", "LINKEDIN", "JOB_FAIR", "WALK_IN", "OTHER");
    private static final Set<String> INTERVIEW_MODES = Set.of("IN_PERSON", "PHONE", "VIDEO");

    private final RecruitmentRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;
    private final com.ams.hrms.service.OnboardingService onboardingService;

    public RecruitmentService(RecruitmentRepository repository, AuditService auditService,
                              EmployeeService employeeService,
                              com.ams.hrms.service.OnboardingService onboardingService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
        this.onboardingService = onboardingService;
    }

    // ------------------------------------------------------------------
    // Vacancies
    // ------------------------------------------------------------------

    public List<JobVacancy> findVacancies(String keyword, String status) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findVacancies(keyword, status);
    }

    public List<JobVacancy> openVacancies() {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findOpenVacancies();
    }

    /** Creates or updates a vacancy; returns the persisted id. */
    public long saveVacancy(JobVacancy vacancy) {
        boolean isNew = vacancy.getId() == null;
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        validateVacancy(vacancy);

        if (isNew) {
            vacancy.setStatus("OPEN");
            vacancy.setCreatedBy(SessionContext.currentUserId());
            long id = repository.insertVacancy(vacancy);
            repository.updateVacancyCode(id, "VAC-" + String.format("%04d", id));
            audit("CREATE", "JobVacancy", id,
                    "Opened vacancy '" + vacancy.getTitle() + "' (" + vacancy.getHeadcount()
                            + " seat(s))");
            publishChange();
            return id;
        }

        JobVacancy existing = requireVacancy(vacancy.getId());
        if (!"OPEN".equals(existing.getStatus()) && !"ON_HOLD".equals(existing.getStatus())) {
            throw new BusinessException("Vacancy is closed",
                    "Only OPEN or ON_HOLD vacancies can be edited.");
        }
        repository.updateVacancy(vacancy);
        audit("UPDATE", "JobVacancy", vacancy.getId(),
                "Updated vacancy '" + vacancy.getVacancyCode() + "'");
        publishChange();
        return vacancy.getId();
    }

    /** Manual status transition (fill/close/hold/cancel). */
    public void setVacancyStatus(long vacancyId, String targetStatus) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        if (!RecruitmentWorkflow.VACANCY_STATUSES.contains(targetStatus)) {
            throw new ValidationException(List.of("Unknown vacancy status."));
        }
        JobVacancy vacancy = requireVacancy(vacancyId);
        if (!RecruitmentWorkflow.canTransitionVacancy(vacancy.getStatus(), targetStatus)) {
            throw new BusinessException("Transition not allowed",
                    "A vacancy cannot move from " + vacancy.getStatus() + " to "
                            + targetStatus + ".");
        }
        repository.updateVacancyStatus(vacancyId, targetStatus);
        audit("STATUS_CHANGE", "JobVacancy", vacancyId,
                "Vacancy '" + vacancy.getVacancyCode() + "' set to " + targetStatus);
        publishChange();
    }

    // ------------------------------------------------------------------
    // Candidates
    // ------------------------------------------------------------------

    public List<Candidate> findCandidates(String keyword, String status) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findCandidates(keyword, status);
    }

    public List<Candidate> activeCandidates() {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findActiveCandidates();
    }

    /**
     * Creates or updates a candidate. When {@code resumeFile} is non-null it
     * is validated and stored; returns the persisted id.
     */
    public long saveCandidate(Candidate candidate) {
        boolean isNew = candidate.getId() == null;
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        validateCandidate(candidate);

        if (candidate.getResumeFile() != null) {
            long folderKey = isNew ? 0 : candidate.getId();
            candidate.setResumePath(FileStorage.storeResume(candidate.getResumeFile(), folderKey));
        }
        if (isNew) {
            candidate.setStatus("NEW");
            long id = repository.insertCandidate(candidate);
            repository.updateCandidateCode(id, "CAN-" + String.format("%04d", id));
            audit("CREATE", "Candidate", id,
                    "Registered candidate '" + candidate.getFullName() + "'");
            publishChange();
            return id;
        }
        repository.updateCandidate(candidate);
        audit("UPDATE", "Candidate", candidate.getId(),
                "Updated candidate '" + candidate.getCandidateCode() + "'");
        publishChange();
        return candidate.getId();
    }

    /**
     * Directly ends a candidate's pipeline (REJECTED/WITHDRAWN) without an
     * application action; only allowed while no active application remains.
     */
    public void exitCandidate(long candidateId, String exitStatus, String reason) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        if (!Set.of("REJECTED", "WITHDRAWN").contains(exitStatus)) {
            throw new ValidationException(
                    List.of("Candidates can only be rejected or withdrawn directly."));
        }
        Candidate candidate = repository.findCandidateById(candidateId)
                .orElseThrow(() -> new BusinessException("Candidate not found",
                        "The candidate no longer exists."));
        boolean hasActiveApplication = repository.findActiveApplications().stream()
                .anyMatch(application -> application.getCandidateId() == candidateId);
        if (hasActiveApplication) {
            throw new BusinessException("Active application exists",
                    "'" + candidate.getFullName() + "' still has an active application."
                            + " Decide on the application instead.");
        }
        if (!RecruitmentWorkflow.canTransitionCandidate(candidate.getStatus(), exitStatus)) {
            throw new BusinessException("Transition not allowed",
                    "A candidate cannot move from " + candidate.getStatus()
                            + " to " + exitStatus + ".");
        }
        repository.updateCandidateStatus(candidateId, exitStatus);
        audit("STATUS_CHANGE", "Candidate", candidateId,
                "Candidate '" + candidate.getCandidateCode() + "' set to " + exitStatus
                        + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));
        publishChange();
    }

    /**
     * Returns a REJECTED or WITHDRAWN candidate to NEW so a fresh application
     * can be filed. Closed applications stay closed; HIRED candidates are not
     * re-openable (an employee record exists - exits go through Separation).
     */
    public void reopenCandidate(long candidateId) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        Candidate candidate = repository.findCandidateById(candidateId)
                .orElseThrow(() -> new BusinessException("Candidate not found",
                        "The candidate no longer exists."));
        if (!RecruitmentWorkflow.canTransitionCandidate(candidate.getStatus(), "NEW")) {
            throw new BusinessException("Cannot re-open",
                    "Only REJECTED or WITHDRAWN candidates can be re-opened. '"
                            + candidate.getFullName() + "' is " + candidate.getStatus() + ".");
        }
        repository.updateCandidateStatus(candidateId, "NEW");
        audit("STATUS_CHANGE", "Candidate", candidateId,
                "Candidate '" + candidate.getCandidateCode() + "' re-opened to NEW by '"
                        + SessionContext.currentUser().username() + "'");
        publishChange();
    }

    // ------------------------------------------------------------------
    // Applications
    // ------------------------------------------------------------------

    public List<JobApplication> findApplications(String keyword, String status, Long vacancyId) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findApplications(keyword, status, vacancyId);
    }

    public List<JobApplication> activeApplications() {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findActiveApplications();
    }

    /** Submits an application for an active candidate to an open vacancy. */
    public long apply(long candidateId, long vacancyId, String coverLetter) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        List<String> coverLetterErrors = new ArrayList<>();

        Candidate candidate = repository.findCandidateById(candidateId)
                .orElseThrow(() -> new BusinessException("Candidate not found",
                        "The candidate no longer exists."));
        if (!RecruitmentWorkflow.candidateActive(candidate.getStatus())) {
            throw new ValidationException(List.of(
                    candidate.getFullName() + " is " + candidate.getStatus()
                            + " and cannot apply."));
        }
        JobVacancy vacancy = repository.findVacancyById(vacancyId)
                .orElseThrow(() -> new BusinessException("Vacancy not found",
                        "The vacancy no longer exists."));
        if (!"OPEN".equals(vacancy.getStatus())) {
            throw new ValidationException(List.of(
                    "Applications are only accepted for OPEN vacancies."));
        }
        if (repository.applicationExists(candidateId, vacancyId)) {
            throw new ValidationException(List.of(
                    "This candidate already has an application for this vacancy."));
        }
        Validators.maxLength(coverLetterErrors, coverLetter, 2000, "Cover letter");
        if (!coverLetterErrors.isEmpty()) {
            throw new ValidationException(coverLetterErrors);
        }

        JobApplication application = new JobApplication();
        application.setCandidateId(candidateId);
        application.setVacancyId(vacancyId);
        application.setApplicationDate(LocalDate.now());
        application.setCoverLetter(Validators.normalize(coverLetter));
        application.setStatus("SUBMITTED");

        TransactionManager.execute(tx -> {
            long id = repository.insertApplication(application);
            repository.updateApplicationCode(id, "APP-" + String.format("%04d", id));
            advanceCandidate(candidateId, "SHORTLISTED",
                    "Application " + id + " submitted");
            application.setId(id);
            application.setApplicationCode("APP-" + String.format("%04d", id));
            return null;
        });

        audit("CREATE", "JobApplication", application.getId(),
                "Application " + application.getApplicationCode() + ": "
                        + candidate.getFullName() + " applied for '" + vacancy.getTitle() + "'");
        publishChange();
        return application.getId();
    }

    /** SUBMITTED -&gt; SCREENING; the candidate moves to SHORTLISTED. */
    public void shortlist(long applicationId) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        JobApplication application = requireActiveApplication(applicationId);
        transitionApplication(application, "SCREENING");
        advanceCandidate(application.getCandidateId(), "SHORTLISTED",
                "Application " + application.getApplicationCode() + " shortlisted");
        audit("STATUS_CHANGE", "JobApplication", applicationId,
                "Application " + application.getApplicationCode() + " moved to SCREENING");
        publishChange();
    }

    /** Withdraws an application; the candidate follows when no pipeline left. */
    public void withdrawApplication(long applicationId) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        JobApplication application = requireActiveApplication(applicationId);
        transitionApplication(application, "WITHDRAWN");
        syncCandidateExit(application.getCandidateId(), "WITHDRAWN",
                "Application " + application.getApplicationCode() + " withdrawn");
        audit("CANCEL", "JobApplication", applicationId,
                "Application " + application.getApplicationCode() + " withdrawn");
        publishChange();
    }

    /** Rejects an application with a reason; mirrors onto the candidate. */
    public void rejectApplication(long applicationId, String reason) {
        SecurityService.require(Permissions.RECRUITMENT_MANAGE);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException(List.of("A rejection reason is required."));
        }
        JobApplication application = requireActiveApplication(applicationId);
        transitionApplication(application, "REJECTED");
        syncCandidateExit(application.getCandidateId(), "REJECTED",
                "Application " + application.getApplicationCode() + " rejected");
        audit("REJECT", "JobApplication", applicationId,
                "Application " + application.getApplicationCode() + " rejected: "
                        + reason.trim());
        publishChange();
    }

    // ------------------------------------------------------------------
    // Interviews
    // ------------------------------------------------------------------

    public List<Interview> findInterviews(String keyword, String result) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findInterviews(keyword, result);
    }

    public List<Interview> interviewsForApplication(long applicationId) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findInterviewsForApplication(applicationId);
    }

    /**
     * Schedules the next interview round for an application in SCREENING or
     * INTERVIEW stage; the application moves to INTERVIEW.
     */
    public long scheduleInterview(Interview interview) {
        SecurityService.require(Permissions.INTERVIEW_MANAGE);
        validateInterview(interview);

        JobApplication application = requireActiveApplication(interview.getApplicationId());
        if (!"SCREENING".equals(application.getStatus())
                && !"INTERVIEW".equals(application.getStatus())) {
            throw new BusinessException("Wrong pipeline stage",
                    "Interviews are scheduled while an application is SCREENING or "
                            + "INTERVIEW.");
        }

        interview.setInterviewRound(repository.nextInterviewRound(interview.getApplicationId()));
        interview.setResult("PENDING");
        long id = repository.insertInterview(interview);

        if ("SCREENING".equals(application.getStatus())) {
            transitionApplication(application, "INTERVIEW");
        }
        advanceCandidate(application.getCandidateId(), "INTERVIEWING",
                "Interview round " + interview.getInterviewRound() + " scheduled");
        audit("CREATE", "Interview", id,
                "Round " + interview.getInterviewRound() + " scheduled for "
                        + application.getCandidateName() + " on "
                        + interview.getInterviewDate());
        publishChange();
        return id;
    }

    /**
     * Records the outcome of a PENDING interview. A PASS keeps the
     * application in INTERVIEW (eligible for offers); a FAIL rejects the
     * application and mirrors onto the candidate.
     */
    public void recordResult(long interviewId, String result, BigDecimal score, String notes) {
        SecurityService.require(Permissions.INTERVIEW_MANAGE);
        List<String> errors = new ArrayList<>();
        if (!Set.of("PASS", "FAIL", "ON_HOLD").contains(result)) {
            errors.add("Interview result must be PASS, FAIL or ON_HOLD.");
        }
        if (score != null && (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(BigDecimal.valueOf(100)) > 0)) {
            errors.add("Score must be between 0 and 100.");
        }
        Validators.maxLength(errors, notes, 1000, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Interview interview = repository.findInterviewById(interviewId)
                .orElseThrow(() -> new BusinessException("Interview not found",
                        "The interview no longer exists."));
        if (!"PENDING".equals(interview.getResult())) {
            throw new BusinessException("Interview already decided",
                    "This interview already has a recorded result.");
        }
        JobApplication application = repository.findApplicationById(interview.getApplicationId())
                .orElseThrow(() -> new BusinessException("Application not found",
                        "The application no longer exists."));

        repository.updateInterviewResult(interviewId, result, score, Validators.normalize(notes));
        if ("FAIL".equals(result) && RecruitmentWorkflow.applicationActive(application.getStatus())
                && !"OFFER".equals(application.getStatus())) {
            transitionApplication(application, "REJECTED");
            syncCandidateExit(application.getCandidateId(), "REJECTED",
                    "Failed interview round " + interview.getInterviewRound());
        }
        audit("UPDATE", "Interview", interviewId,
                "Round " + interview.getInterviewRound() + " for "
                        + interview.getCandidateName() + " recorded as " + result
                        + (score == null ? "" : " (" + score.toPlainString() + "/100)"));
        publishChange();
    }

    // ------------------------------------------------------------------
    // Offers
    // ------------------------------------------------------------------

    public List<JobOffer> findOffers(String keyword, String status) {
        SecurityService.require(Permissions.RECRUITMENT_VIEW);
        return repository.findOffers(keyword, status);
    }

    /**
     * Creates a DRAFT offer for an application that has a passed interview.
     * The offered salary must sit inside the position envelope when defined.
     */
    public long createOffer(JobOffer offer) {
        SecurityService.require(Permissions.OFFER_MANAGE);
        validateOfferDates(offer);

        JobApplication application = repository.findApplicationById(offer.getApplicationId())
                .orElseThrow(() -> new BusinessException("Application not found",
                        "The application no longer exists."));
        if (!"INTERVIEW".equals(application.getStatus())) {
            throw new BusinessException("Wrong pipeline stage",
                    "Offers are created for applications in the INTERVIEW stage with a "
                            + "passed interview.");
        }
        if (!repository.hasPassedInterview(application.getId())) {
            throw new ValidationException(List.of(
                    "At least one PASSED interview is required before an offer."));
        }
        JobVacancy vacancy = repository.findVacancyById(application.getVacancyId())
                .orElseThrow(() -> new BusinessException("Vacancy not found",
                        "The vacancy no longer exists."));
        if (vacancy.remainingHeadcount() <= 0) {
            throw new BusinessException("No headcount left",
                    "Vacancy '" + vacancy.getTitle() + "' has no remaining seats.");
        }
        enforceSalaryEnvelope(offer.getOfferedSalary(), vacancy.getPositionId());

        offer.setPositionId(vacancy.getPositionId());
        offer.setStatus("DRAFT");

        TransactionManager.execute(tx -> {
            long id = repository.insertOffer(offer);
            repository.updateOfferCode(id, "OFF-" + String.format("%04d", id));
            offer.setId(id);
            offer.setOfferCode("OFF-" + String.format("%04d", id));
            transitionApplication(application, "OFFER");
            advanceCandidate(application.getCandidateId(), "OFFERED",
                    "Offer " + offer.getOfferCode() + " drafted");
            return null;
        });

        audit("CREATE", "JobOffer", offer.getId(),
                "Draft offer " + offer.getOfferCode() + " for "
                        + application.getCandidateName() + " ("
                        + offer.getOfferedSalary().toPlainString() + ")");
        publishChange();
        return offer.getId();
    }

    /** DRAFT -&gt; SENT. */
    public void sendOffer(long offerId) {
        SecurityService.require(Permissions.OFFER_MANAGE);
        JobOffer offer = requireOffer(offerId, "DRAFT");
        transitionOffer(offer, "SENT");
        audit("STATUS_CHANGE", "JobOffer", offerId,
                "Offer " + offer.getOfferCode() + " sent to " + offer.getCandidateName());
        publishChange();
    }

    /** SENT -&gt; ACCEPTED; the application moves to ACCEPTED. */
    public void acceptOffer(long offerId) {
        SecurityService.require(Permissions.OFFER_MANAGE);
        JobOffer offer = requireOffer(offerId, "SENT");
        JobApplication application = repository.findApplicationById(offer.getApplicationId())
                .orElseThrow(() -> new BusinessException("Application not found",
                        "The application no longer exists."));

        TransactionManager.execute(tx -> {
            repository.updateOfferStatus(offerId, "ACCEPTED");
            if ("OFFER".equals(application.getStatus())) {
                repository.updateApplicationStatus(application.getId(), "ACCEPTED");
            }
            return null;
        });
        audit("STATUS_CHANGE", "JobOffer", offerId,
                "Offer " + offer.getOfferCode() + " accepted by "
                        + offer.getCandidateName());
        publishChange();
    }

    /** SENT/DRAFT -&gt; DECLINED/CANCELLED/EXPIRED with pipeline cleanup. */
    public void closeOffer(long offerId, String targetStatus) {
        SecurityService.require(Permissions.OFFER_MANAGE);
        if (!Set.of("DECLINED", "CANCELLED", "EXPIRED").contains(targetStatus)) {
            throw new ValidationException(List.of(
                    "Offers can only be closed as DECLINED, CANCELLED or EXPIRED."));
        }
        JobOffer offer = repository.findOfferById(offerId)
                .orElseThrow(() -> new BusinessException("Offer not found",
                        "The offer no longer exists."));
        transitionOffer(offer, targetStatus);

        JobApplication application = repository.findApplicationById(offer.getApplicationId())
                .orElse(null);
        if (application != null && "OFFER".equals(application.getStatus())) {
            transitionApplication(application, "REJECTED");
            syncCandidateExit(application.getCandidateId(), "REJECTED",
                    "Offer " + offer.getOfferCode() + " " + targetStatus.toLowerCase());
        }
        audit("STATUS_CHANGE", "JobOffer", offerId,
                "Offer " + offer.getOfferCode() + " set to " + targetStatus);
        publishChange();
    }

    /**
     * Hires the candidate of an ACCEPTED offer (spec section 14): creates
     * the employee record (own transaction via {@link EmployeeService}),
     * then links the offer, marks the candidate HIRED, fills the vacancy
     * when the headcount is exhausted and generates the onboarding
     * checklist (spec section 15). Two sequential transactional phases:
     * {@link com.ams.hrms.repository.TransactionManager} rejects nesting.
     *
     * @return the new employee id
     */
    public long hire(long offerId, LocalDate joinDate) {
        SecurityService.require(Permissions.OFFER_MANAGE);
        JobOffer offer = repository.findOfferById(offerId)
                .orElseThrow(() -> new BusinessException("Offer not found",
                        "The offer no longer exists."));
        if (!"ACCEPTED".equals(offer.getStatus())) {
            throw new BusinessException("Offer not accepted",
                    "Only ACCEPTED offers can be hired.");
        }
        if (offer.getEmployeeId() != null) {
            throw new BusinessException("Already hired",
                    "This offer has already been converted to an employee.");
        }
        LocalDate effectiveJoinDate = joinDate != null ? joinDate : offer.getJoiningDate();

        JobApplication application = repository.findApplicationById(offer.getApplicationId())
                .orElseThrow(() -> new BusinessException("Application not found",
                        "The application no longer exists."));
        Candidate candidate = repository.findCandidateById(application.getCandidateId())
                .orElseThrow(() -> new BusinessException("Candidate not found",
                        "The candidate no longer exists."));
        JobVacancy vacancy = repository.findVacancyById(application.getVacancyId())
                .orElseThrow(() -> new BusinessException("Vacancy not found",
                        "The vacancy no longer exists."));

        Employee hire = buildEmployee(candidate, vacancy, offer, effectiveJoinDate);

        long employeeId = employeeService.save(hire);

        TransactionManager.execute(tx -> {
            repository.linkOfferEmployee(offerId, employeeId);
            repository.updateCandidateStatus(candidate.getId(), "HIRED");
            if ("OPEN".equals(vacancy.getStatus())
                    && repository.acceptedApplicationCount(vacancy.getId())
                            >= vacancy.getHeadcount()) {
                repository.updateVacancyStatus(vacancy.getId(), "FILLED");
                audit("STATUS_CHANGE", "JobVacancy", vacancy.getId(),
                        "Vacancy '" + vacancy.getTitle() + "' filled");
            }
            onboardingService.generateChecklist(employeeId,
                    effectiveJoinDate.plusDays(
                            com.ams.hrms.service.OnboardingService.DEFAULT_DUE_DAYS));
            return null;
        });

        audit("HIRE", "JobOffer", offerId,
                "Hired " + candidate.getFullName() + " as employee '"
                        + hire.getCode() + "' via offer " + offer.getOfferCode());
        LOG.info("Candidate {} hired as {}", candidate.getCandidateCode(), hire.getCode());
        publishChange();
        return employeeId;
    }

    /** Expires SENT offers past their expiry date; called by dev tools/startup hooks. */
    public int expireStaleOffers() {
        int expired = repository.expireStaleOffers();
        if (expired > 0) {
            LOG.info("Expired {} stale job offer(s)", expired);
            publishChange();
        }
        return expired;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validateVacancy(JobVacancy vacancy) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, vacancy.getTitle(), "Title");
        Validators.maxLength(errors, vacancy.getTitle(), 150, "Title");
        if (vacancy.getDepartmentId() == null || vacancy.getDepartmentId() <= 0) {
            errors.add("Department is required.");
        }
        if (vacancy.getPositionId() == null || vacancy.getPositionId() <= 0) {
            errors.add("Position is required.");
        }
        if (vacancy.getHeadcount() < 1 || vacancy.getHeadcount() > 999) {
            errors.add("Headcount must be between 1 and 999.");
        }
        if (vacancy.getEmploymentType() == null
                || !EMPLOYMENT_TYPES.contains(vacancy.getEmploymentType())) {
            errors.add("Employment type is invalid.");
        }
        if (vacancy.getOpeningDate() == null) {
            errors.add("Opening date is required.");
        }
        if (vacancy.getOpeningDate() != null && vacancy.getClosingDate() != null
                && vacancy.getClosingDate().isBefore(vacancy.getOpeningDate())) {
            errors.add("Closing date cannot be before the opening date.");
        }
        Validators.nonNegative(errors, vacancy.getSalaryMin(), "Minimum salary");
        Validators.nonNegative(errors, vacancy.getSalaryMax(), "Maximum salary");
        Validators.salaryRange(errors, vacancy.getSalaryMin(), vacancy.getSalaryMax());
        Validators.maxLength(errors, vacancy.getJobDescription(), 5000, "Description");
        Validators.maxLength(errors, vacancy.getRequirements(), 5000, "Requirements");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        vacancy.setTitle(Validators.normalize(vacancy.getTitle()));
        vacancy.setJobDescription(Validators.normalize(vacancy.getJobDescription()));
        vacancy.setRequirements(Validators.normalize(vacancy.getRequirements()));

        if (repository.vacancyCodeExists(Validators.normalize(vacancy.getVacancyCode()),
                vacancy.getId())) {
            throw new ValidationException(
                    List.of("Vacancy code is already in use."));
        }
    }

    private void validateCandidate(Candidate candidate) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, candidate.getFirstName(), "First name");
        Validators.required(errors, candidate.getLastName(), "Last name");
        Validators.maxLength(errors, candidate.getFirstName(), 75, "First name");
        Validators.maxLength(errors, candidate.getLastName(), 75, "Last name");
        Validators.required(errors, candidate.getPhone(), "Phone");
        Validators.phone(errors, candidate.getPhone(), "Phone");
        Validators.email(errors, candidate.getEmail(), "Email");
        Validators.maxLength(errors, candidate.getAddress(), 300, "Address");
        Validators.maxLength(errors, candidate.getSkills(), 500, "Skills");

        if (candidate.getSource() == null || !SOURCES.contains(candidate.getSource())) {
            errors.add("Source is invalid.");
        }
        if (candidate.getGender() != null && !candidate.getGender().isBlank()
                && !Set.of("MALE", "FEMALE", "OTHER").contains(candidate.getGender())) {
            errors.add("Gender must be MALE, FEMALE or OTHER.");
        }
        if (candidate.getDateOfBirth() != null
                && !candidate.getDateOfBirth().isBefore(LocalDate.now())) {
            errors.add("Date of birth must be in the past.");
        }
        if (candidate.getExperienceYears() != null
                && (candidate.getExperienceYears().signum() < 0
                        || candidate.getExperienceYears().compareTo(BigDecimal.valueOf(60)) > 0)) {
            errors.add("Experience must be between 0 and 60 years.");
        }
        Validators.nonNegative(errors, candidate.getExpectedSalary(), "Expected salary");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        candidate.setFirstName(Validators.normalize(candidate.getFirstName()));
        candidate.setLastName(Validators.normalize(candidate.getLastName()));
        candidate.setEmail(Validators.normalize(candidate.getEmail()).isEmpty()
                ? null : Validators.normalize(candidate.getEmail()));
        candidate.setPhone(Validators.normalize(candidate.getPhone()));
        candidate.setGender(candidate.getGender() == null || candidate.getGender().isBlank()
                ? null : candidate.getGender());

        if (repository.candidateCodeExists(Validators.normalize(candidate.getCandidateCode()),
                candidate.getId())) {
            throw new ValidationException(
                    List.of("Candidate code is already in use."));
        }
    }

    private void validateInterview(Interview interview) {
        List<String> errors = new ArrayList<>();
        if (interview.getApplicationId() <= 0) {
            errors.add("Application is required.");
        }
        if (interview.getInterviewDate() == null) {
            errors.add("Interview date/time is required.");
        } else if (interview.getInterviewDate().isBefore(LocalDateTime.now().minusMinutes(5))) {
            errors.add("Interview date/time cannot be in the past.");
        }
        if (interview.getMode() == null || !INTERVIEW_MODES.contains(interview.getMode())) {
            errors.add("Interview mode must be IN_PERSON, PHONE or VIDEO.");
        }
        Validators.maxLength(errors, interview.getNotes(), 1000, "Notes");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void validateOfferDates(JobOffer offer) {
        List<String> errors = new ArrayList<>();
        if (offer.getOfferedSalary() == null) {
            errors.add("Offered salary is required.");
        } else if (offer.getOfferedSalary().signum() < 0) {
            errors.add("Offered salary cannot be negative.");
        }
        if (offer.getOfferDate() == null) {
            errors.add("Offer date is required.");
        }
        if (offer.getExpiryDate() != null && offer.getOfferDate() != null
                && offer.getExpiryDate().isBefore(offer.getOfferDate())) {
            errors.add("Expiry date cannot be before the offer date.");
        }
        if (offer.getJoiningDate() != null && offer.getOfferDate() != null
                && offer.getJoiningDate().isBefore(offer.getOfferDate())) {
            errors.add("Joining date cannot be before the offer date.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void enforceSalaryEnvelope(BigDecimal offeredSalary, long positionId) {
        var position = new com.ams.hrms.repository.PositionRepository().findById(positionId);
        if (position.isEmpty()) {
            return;
        }
        BigDecimal min = position.get().getMinSalary();
        BigDecimal max = position.get().getMaxSalary();
        if (min != null && offeredSalary.compareTo(min) < 0) {
            throw new ValidationException(List.of(
                    "Offered salary is below the position minimum of "
                            + min.toPlainString() + "."));
        }
        if (max != null && offeredSalary.compareTo(max) > 0) {
            throw new ValidationException(List.of(
                    "Offered salary is above the position maximum of "
                            + max.toPlainString() + "."));
        }
    }

    // ------------------------------------------------------------------
    // Hire conversion
    // ------------------------------------------------------------------

    private Employee buildEmployee(Candidate candidate, JobVacancy vacancy,
                                   JobOffer offer, LocalDate joinDate) {
        if (candidate.getGender() == null || candidate.getDateOfBirth() == null) {
            throw new ValidationException(List.of(
                    "Gender and date of birth are required before hiring. "
                            + "Update candidate '" + candidate.getCandidateCode()
                            + "' first."));
        }
        if (joinDate == null) {
            throw new ValidationException(List.of(
                    "A joining date is required to complete the hire."));
        }

        Employee employee = new Employee();
        employee.setCode(new com.ams.hrms.repository.EmployeeRepository().nextEmployeeCode());
        employee.setFirstName(candidate.getFirstName());
        employee.setLastName(candidate.getLastName());
        employee.setGender(candidate.getGender());
        employee.setDateOfBirth(candidate.getDateOfBirth());
        employee.setEmail(candidate.getEmail());
        employee.setPhone(candidate.getPhone());
        employee.setAddress(candidate.getAddress());
        employee.setJoinDate(joinDate);
        employee.setEmploymentType(vacancy.getEmploymentType());
        employee.setDepartmentId(vacancy.getDepartmentId());
        employee.setPositionId(vacancy.getPositionId());
        employee.setBasicSalary(offer.getOfferedSalary());
        employee.setStatus("ACTIVE");
        return employee;
    }

    // ------------------------------------------------------------------
    // Pipeline helpers
    // ------------------------------------------------------------------

    private JobVacancy requireVacancy(long id) {
        return repository.findVacancyById(id).orElseThrow(() ->
                new BusinessException("Vacancy not found", "The vacancy no longer exists."));
    }

    private JobApplication requireActiveApplication(long id) {
        JobApplication application = repository.findApplicationById(id)
                .orElseThrow(() -> new BusinessException("Application not found",
                        "The application no longer exists."));
        if (!RecruitmentWorkflow.applicationActive(application.getStatus())) {
            throw new BusinessException("Application is decided",
                    "Only active applications can be actioned.");
        }
        return application;
    }

    private JobOffer requireOffer(long id, String expectedStatus) {
        JobOffer offer = repository.findOfferById(id)
                .orElseThrow(() -> new BusinessException("Offer not found",
                        "The offer no longer exists."));
        if (!expectedStatus.equals(offer.getStatus())) {
            throw new BusinessException("Unexpected offer status",
                    "This action needs an offer in " + expectedStatus
                            + " but it is " + offer.getStatus() + ".");
        }
        return offer;
    }

    /** Applies one legal application transition or fails loudly. */
    private void transitionApplication(JobApplication application, String target) {
        if (!RecruitmentWorkflow.canTransitionApplication(application.getStatus(), target)) {
            throw new BusinessException("Transition not allowed",
                    "An application cannot move from " + application.getStatus()
                            + " to " + target + ".");
        }
        repository.updateApplicationStatus(application.getId(), target);
        application.setStatus(target);
    }

    /** Applies one legal offer transition or fails loudly. */
    private void transitionOffer(JobOffer offer, String target) {
        if (!RecruitmentWorkflow.canTransitionOffer(offer.getStatus(), target)) {
            throw new BusinessException("Transition not allowed",
                    "An offer cannot move from " + offer.getStatus() + " to " + target + ".");
        }
        repository.updateOfferStatus(offer.getId(), target);
        offer.setStatus(target);
    }

    /**
     * Moves the candidate forward when legal; a candidate already at a later
     * stage stays there.
     */
    private void advanceCandidate(long candidateId, String target, String reason) {
        Candidate candidate = repository.findCandidateById(candidateId).orElse(null);
        if (candidate == null || candidate.getStatus().equals(target)
                || !RecruitmentWorkflow.canTransitionCandidate(candidate.getStatus(), target)) {
            return;
        }
        repository.updateCandidateStatus(candidateId, target);
        audit("STATUS_CHANGE", "Candidate", candidateId,
                "Candidate '" + candidate.getCandidateCode() + "' moved to " + target
                        + " (" + reason + ")");
    }

    /**
     * Ends a candidate's pipeline (REJECTED/WITHDRAWN) only when no other
     * active application remains.
     */
    private void syncCandidateExit(long candidateId, String exitStatus, String reason) {
        Candidate candidate = repository.findCandidateById(candidateId).orElse(null);
        if (candidate == null || !RecruitmentWorkflow.candidateActive(candidate.getStatus())) {
            return;
        }
        boolean stillInPipeline = repository.findActiveCandidates().stream()
                .anyMatch(active -> active.getId() == candidateId);
        if (stillInPipeline) {
            return;
        }
        if (!RecruitmentWorkflow.canTransitionCandidate(candidate.getStatus(), exitStatus)) {
            return;
        }
        repository.updateCandidateStatus(candidateId, exitStatus);
        audit("STATUS_CHANGE", "Candidate", candidateId,
                "Candidate '" + candidate.getCandidateCode() + "' set to " + exitStatus
                        + " (" + reason + ")");
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "RECRUITMENT", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged(EmployeeService.DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}

package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.RecruitmentController;
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.service.RecruitmentWorkflow;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Recruitment module (spec section 14): vacancies, candidates, application
 * pipeline, interviews and offers - each tab follows the shared
 * toolbar/table/context-menu pattern; all workflow actions are permission
 * gated in the menu, in the controller's service call and by RBAC at the
 * service boundary.
 */
public class RecruitmentPanel extends JPanel {

    private final RecruitmentController controller =
            new RecruitmentController(ServiceRegistry.recruitmentService());

    // --- vacancies tab ---
    private final SearchField vacancySearch = new SearchField("Search title, code, department...");
    private final JComboBox<String> vacancyStatusFilter = new JComboBox<>(new String[]{
            "All Statuses", "OPEN", "ON_HOLD", "FILLED", "CLOSED", "CANCELLED"});
    private final SecureButton newVacancyButton =
            new SecureButton("New Vacancy", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.RECRUITMENT_MANAGE);
    private final HrmsTable vacancyTable = HrmsTable.builder(
                    "ID", "Code", "Title", "Department", "Position", "Type",
                    "Seats", "Filled", "Opening", "Closing", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(5, 95)
            .fixedColumn(6, 65)
            .fixedColumn(7, 60)
            .fixedColumn(8, 95)
            .fixedColumn(9, 95)
            .badgeColumn(10)
            .contextMenu(this::buildVacancyMenu)
            .build();
    private List<JobVacancy> loadedVacancies = List.of();

    // --- candidates tab ---
    private final SearchField candidateSearch = new SearchField("Search name, code, phone...");
    private final JComboBox<String> candidateStatusFilter = new JComboBox<>(withAll(
            RecruitmentWorkflow.CANDIDATE_STATUSES));
    private final SecureButton newCandidateButton =
            new SecureButton("New Candidate", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.RECRUITMENT_MANAGE);
    private final HrmsTable candidateTable = HrmsTable.builder(
                    "ID", "Code", "Name", "Phone", "Email", "Source",
                    "Exp. (yrs)", "Expected", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(5, 95)
            .fixedColumn(6, 80)
            .fixedColumn(7, 95)
            .badgeColumn(8)
            .contextMenu(this::buildCandidateMenu)
            .build();
    private List<Candidate> loadedCandidates = List.of();

    // --- applications tab ---
    private final SearchField applicationSearch = new SearchField("Search candidate, code, vacancy...");
    private final JComboBox<String> applicationStatusFilter = new JComboBox<>(withAll(
            RecruitmentWorkflow.APPLICATION_STATUSES));
    private final SecureButton newApplicationButton =
            new SecureButton("New Application", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.RECRUITMENT_MANAGE);
    private final HrmsTable applicationTable = HrmsTable.builder(
                    "ID", "Code", "Candidate", "Vacancy", "Applied", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(4, 95)
            .badgeColumn(5)
            .contextMenu(this::buildApplicationMenu)
            .build();
    private List<JobApplication> loadedApplications = List.of();

    // --- interviews tab ---
    private final SearchField interviewSearch = new SearchField("Search candidate or vacancy...");
    private final JComboBox<String> interviewResultFilter = new JComboBox<>(new String[]{
            "All Results", "PENDING", "PASS", "FAIL", "ON_HOLD"});
    private final SecureButton scheduleInterviewButton =
            new SecureButton("Schedule Interview", "calendar", ModernButton.Variant.PRIMARY,
                    Permissions.INTERVIEW_MANAGE);
    private final HrmsTable interviewTable = HrmsTable.builder(
                    "ID", "Round", "Candidate", "Vacancy", "Date & Time", "Mode",
                    "Interviewer", "Score", "Result")
            .hiddenColumn(0)
            .fixedColumn(1, 65)
            .fixedColumn(4, 145)
            .fixedColumn(5, 90)
            .fixedColumn(7, 70)
            .badgeColumn(8)
            .contextMenu(this::buildInterviewMenu)
            .build();
    private List<Interview> loadedInterviews = List.of();

    // --- offers tab ---
    private final SearchField offerSearch = new SearchField("Search candidate or code...");
    private final JComboBox<String> offerStatusFilter = new JComboBox<>(withAll(
            RecruitmentWorkflow.OFFER_STATUSES));
    private final HrmsTable offerTable = HrmsTable.builder(
                    "ID", "Code", "Candidate", "Position", "Salary",
                    "Offered", "Expires", "Joining", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(4, 100)
            .fixedColumn(5, 95)
            .fixedColumn(6, 95)
            .fixedColumn(7, 95)
            .badgeColumn(8)
            .contextMenu(this::buildOfferMenu)
            .build();
    private List<JobOffer> loadedOffers = List.of();

    public RecruitmentPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Vacancies", buildToolbarTableTab(vacancySearch, vacancyStatusFilter,
                newVacancyButton, vacancyTable));
        tabs.addTab("Candidates", buildToolbarTableTab(candidateSearch, candidateStatusFilter,
                newCandidateButton, candidateTable));
        tabs.addTab("Applications", buildToolbarTableTab(applicationSearch,
                applicationStatusFilter, newApplicationButton, applicationTable));
        tabs.addTab("Interviews", buildToolbarTableTab(interviewSearch, interviewResultFilter,
                scheduleInterviewButton, interviewTable));
        tabs.addTab("Offers", buildToolbarTableTab(offerSearch, offerStatusFilter, null,
                offerTable));
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        refreshAll();
    }

    // ------------------------------------------------------------------
    // Construction helpers
    // ------------------------------------------------------------------

    private JPanel buildToolbarTableTab(SearchField search, JComboBox<String> filter,
                                        SecureButton actionButton, HrmsTable table) {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(search);
        toolbar.add(filter, "width 130!");
        if (actionButton != null) {
            toolbar.add(actionButton);
        }

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(table), BorderLayout.CENTER);
        return tab;
    }

    /** Status options sorted after the leading "All" entry for stable filters. */
    private static String[] withAll(java.util.Collection<String> statuses) {
        List<String> sorted = new ArrayList<>(statuses);
        java.util.Collections.sort(sorted);
        List<String> options = new ArrayList<>();
        options.add("All Statuses");
        options.addAll(sorted);
        return options.toArray(new String[0]);
    }

    private void wireEvents() {
        vacancySearch.onTextChanged(text -> refreshVacancies());
        vacancyStatusFilter.addActionListener(event -> refreshVacancies());
        newVacancyButton.addActionListener(event -> openVacancyDialog(null));

        candidateSearch.onTextChanged(text -> refreshCandidates());
        candidateStatusFilter.addActionListener(event -> refreshCandidates());
        newCandidateButton.addActionListener(event -> openCandidateDialog(null));

        applicationSearch.onTextChanged(text -> refreshApplications());
        applicationStatusFilter.addActionListener(event -> refreshApplications());
        newApplicationButton.addActionListener(event -> openApplicationDialog());

        interviewSearch.onTextChanged(text -> refreshInterviews());
        interviewResultFilter.addActionListener(event -> refreshInterviews());
        scheduleInterviewButton.addActionListener(event -> openScheduleDialog(null));

        offerSearch.onTextChanged(text -> refreshOffers());
        offerStatusFilter.addActionListener(event -> refreshOffers());
    }

    private void refreshAll() {
        refreshVacancies();
        refreshCandidates();
        refreshApplications();
        refreshInterviews();
        refreshOffers();
    }

    // ------------------------------------------------------------------
    // Data loading
    // ------------------------------------------------------------------

    private void refreshVacancies() {
        String status = selectedStatus(vacancyStatusFilter);
        controller.loadVacancies(vacancySearch.getText(), status, vacancies -> {
            loadedVacancies = vacancies;
            List<Object[]> rows = new ArrayList<>();
            for (var vacancy : vacancies) {
                rows.add(new Object[]{
                        vacancy.getId(),
                        vacancy.getVacancyCode(),
                        vacancy.getTitle(),
                        vacancy.getDepartmentName(),
                        vacancy.getPositionName(),
                        vacancy.getEmploymentType(),
                        vacancy.getHeadcount(),
                        vacancy.getAcceptedCount(),
                        vacancy.getOpeningDate() == null ? "-" : vacancy.getOpeningDate(),
                        vacancy.getClosingDate() == null ? "-" : vacancy.getClosingDate(),
                        vacancy.getStatus()});
            }
            vacancyTable.setRows(rows);
        });
    }

    private void refreshCandidates() {
        String status = selectedStatus(candidateStatusFilter);
        controller.loadCandidates(candidateSearch.getText(), status, candidates -> {
            loadedCandidates = candidates;
            List<Object[]> rows = new ArrayList<>();
            for (var candidate : candidates) {
                rows.add(new Object[]{
                        candidate.getId(),
                        candidate.getCandidateCode(),
                        candidate.getFullName(),
                        candidate.getPhone(),
                        candidate.getEmail() == null ? "-" : candidate.getEmail(),
                        candidate.getSource(),
                        candidate.getExperienceYears() == null
                                ? "-" : candidate.getExperienceYears().toPlainString(),
                        candidate.getExpectedSalary() == null
                                ? "-" : candidate.getExpectedSalary().toPlainString(),
                        candidate.getStatus()});
            }
            candidateTable.setRows(rows);
        });
    }

    private void refreshApplications() {
        String status = selectedStatus(applicationStatusFilter);
        controller.loadApplications(applicationSearch.getText(), status, null, applications -> {
            loadedApplications = applications;
            List<Object[]> rows = new ArrayList<>();
            for (var application : applications) {
                rows.add(new Object[]{
                        application.getId(),
                        application.getApplicationCode(),
                        application.getCandidateName(),
                        application.getVacancyTitle(),
                        application.getApplicationDate() == null
                                ? "-" : application.getApplicationDate(),
                        application.getStatus()});
            }
            applicationTable.setRows(rows);
        });
    }

    private void refreshInterviews() {
        String result = interviewResultFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(interviewResultFilter.getSelectedItem());
        controller.loadInterviews(interviewSearch.getText(), result, interviews -> {
            loadedInterviews = interviews;
            List<Object[]> rows = new ArrayList<>();
            for (var interview : interviews) {
                rows.add(new Object[]{
                        interview.getId(),
                        interview.getInterviewRound(),
                        interview.getCandidateName(),
                        interview.getVacancyTitle(),
                        interview.getInterviewDate()
                                .format(java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm")),
                        interview.getMode(),
                        interview.getInterviewerName() == null
                                ? "-" : interview.getInterviewerName(),
                        interview.getScore() == null ? "-" : interview.getScore().toPlainString(),
                        interview.getResult()});
            }
            interviewTable.setRows(rows);
        });
    }

    private void refreshOffers() {
        String status = selectedStatus(offerStatusFilter);
        controller.loadOffers(offerSearch.getText(), status, offers -> {
            loadedOffers = offers;
            List<Object[]> rows = new ArrayList<>();
            for (var offer : offers) {
                rows.add(new Object[]{
                        offer.getId(),
                        offer.getOfferCode(),
                        offer.getCandidateName(),
                        offer.getPositionTitle(),
                        offer.getOfferedSalary().toPlainString(),
                        offer.getOfferDate() == null ? "-" : offer.getOfferDate(),
                        offer.getExpiryDate() == null ? "-" : offer.getExpiryDate(),
                        offer.getJoiningDate() == null ? "-" : offer.getJoiningDate(),
                        offer.getStatus()});
            }
            offerTable.setRows(rows);
        });
    }

    private String selectedStatus(JComboBox<String> filter) {
        return filter.getSelectedIndex() <= 0 ? "" : String.valueOf(filter.getSelectedItem());
    }

    // ------------------------------------------------------------------
    // Selection helpers
    // ------------------------------------------------------------------

    private JobVacancy selectedVacancy() {
        return findById(vacancyTable, 0, () -> loadedVacancies, JobVacancy::getId);
    }

    private Candidate selectedCandidate() {
        return findById(candidateTable, 0, () -> loadedCandidates, Candidate::getId);
    }

    private JobApplication selectedApplication() {
        return findById(applicationTable, 0, () -> loadedApplications, JobApplication::getId);
    }

    private Interview selectedInterview() {
        return findById(interviewTable, 0, () -> loadedInterviews, Interview::getId);
    }

    private JobOffer selectedOffer() {
        return findById(offerTable, 0, () -> loadedOffers, JobOffer::getId);
    }

    private <T> T findById(HrmsTable table, int idColumn, Supplier<List<T>> source,
                           java.util.function.Function<T, Long> idGetter) {
        Object value = table.selectedValue(idColumn);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        for (T item : source.get()) {
            Long id = idGetter.apply(item);
            if (id != null && id == target) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Vacancy actions
    // ------------------------------------------------------------------

    private void openVacancyDialog(JobVacancy existing) {
        UiThread.executeAsync("Load vacancy dialog data",
                () -> new Object[]{
                        ServiceRegistry.departmentService().findAll(""),
                        ServiceRegistry.positionService().findAll("")},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    var departments = (List<com.ams.hrms.model.Department>) parts[0];
                    @SuppressWarnings("unchecked")
                    var positions = (List<com.ams.hrms.model.Position>) parts[1];

                    VacancyDialog dialog = new VacancyDialog(swingWindow(),
                            departments, positions, existing);
                    if (dialog.showDialog() == VacancyDialog.Result.SAVED) {
                        refreshVacancies();
                    }
                });
    }

    private void setVacancyStatus(JobVacancy vacancy, String target) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Vacancy",
                "Set vacancy '" + vacancy.getVacancyCode() + "' (" + vacancy.getTitle()
                        + ") to " + target + "?");
        if (!confirmed) {
            return;
        }
        controller.setVacancyStatus(vacancy.getId(), target, this::refreshVacancies,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildVacancyMenu() {
        JPopupMenu menu = new JPopupMenu();
        var vacancy = selectedVacancy();
        boolean manage = SecurityService.can(Permissions.RECRUITMENT_MANAGE);
        boolean editable = vacancy != null && manage
                && ("OPEN".equals(vacancy.getStatus()) || "ON_HOLD".equals(vacancy.getStatus()));

        JMenuItem edit = new JMenuItem("Edit Vacancy");
        edit.setEnabled(editable);
        edit.addActionListener(event -> openVacancyDialog(selectedVacancy()));

        JMenuItem hold = new JMenuItem("Put On Hold");
        hold.setEnabled(editable && "OPEN".equals(vacancy.getStatus()));
        hold.addActionListener(event -> setVacancyStatus(selectedVacancy(), "ON_HOLD"));

        JMenuItem reopen = new JMenuItem("Reopen");
        reopen.setEnabled(editable && "ON_HOLD".equals(vacancy.getStatus()));
        reopen.addActionListener(event -> setVacancyStatus(selectedVacancy(), "OPEN"));

        JMenuItem fill = new JMenuItem("Mark Filled");
        fill.setEnabled(editable && "OPEN".equals(vacancy.getStatus()));
        fill.addActionListener(event -> setVacancyStatus(selectedVacancy(), "FILLED"));

        JMenuItem close = new JMenuItem("Close");
        close.setEnabled(editable);
        close.addActionListener(event -> setVacancyStatus(selectedVacancy(), "CLOSED"));

        JMenuItem cancel = new JMenuItem("Cancel");
        cancel.setEnabled(editable);
        cancel.addActionListener(event -> setVacancyStatus(selectedVacancy(), "CANCELLED"));

        menu.add(edit);
        menu.addSeparator();
        menu.add(hold);
        menu.add(reopen);
        menu.add(fill);
        menu.add(close);
        menu.add(cancel);
        return menu;
    }

    // ------------------------------------------------------------------
    // Candidate actions
    // ------------------------------------------------------------------

    private void openCandidateDialog(Candidate existing) {
        CandidateDialog dialog = new CandidateDialog(swingWindow(), existing);
        if (dialog.showDialog() == CandidateDialog.Result.SAVED) {
            refreshCandidates();
        }
    }

    private JPopupMenu buildCandidateMenu() {
        JPopupMenu menu = new JPopupMenu();
        var candidate = selectedCandidate();
        boolean manage = SecurityService.can(Permissions.RECRUITMENT_MANAGE);
        boolean active = candidate != null && RecruitmentWorkflow.candidateActive(
                candidate.getStatus());

        JMenuItem edit = new JMenuItem("Edit Candidate");
        edit.setEnabled(candidate != null && manage);
        edit.addActionListener(event -> openCandidateDialog(selectedCandidate()));

        JMenuItem reject = new JMenuItem("Reject");
        reject.setEnabled(active && manage);
        reject.addActionListener(event -> rejectCandidate(selectedCandidate()));

        JMenuItem withdraw = new JMenuItem("Mark Withdrawn");
        withdraw.setEnabled(active && manage);
        withdraw.addActionListener(event -> withdrawCandidate(selectedCandidate()));

        menu.add(edit);
        menu.addSeparator();
        menu.add(reject);
        menu.add(withdraw);
        return menu;
    }

    private void rejectCandidate(Candidate candidate) {
        String reason = ReasonDialog.show(swingWindow(),
                "Reject Candidate", "Reason for rejecting '" + candidate.getFullName() + "':");
        if (reason == null) {
            return;
        }
        controller.exitCandidate(candidate.getId(), "REJECTED", reason, this::refreshAll,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void withdrawCandidate(Candidate candidate) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Withdraw",
                "Mark candidate '" + candidate.getFullName() + "' as WITHDRAWN?");
        if (!confirmed) {
            return;
        }
        controller.exitCandidate(candidate.getId(), "WITHDRAWN", null, this::refreshAll,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    // ------------------------------------------------------------------
    // Application actions
    // ------------------------------------------------------------------

    private void openApplicationDialog() {
        UiThread.executeAsync("Load application dialog data",
                () -> new Object[]{
                        ServiceRegistry.recruitmentService().activeCandidates(),
                        ServiceRegistry.recruitmentService().openVacancies()},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    var candidates = (List<Candidate>) parts[0];
                    @SuppressWarnings("unchecked")
                    var vacancies = (List<JobVacancy>) parts[1];
                    if (candidates.isEmpty() || vacancies.isEmpty()) {
                        Dialogs.info(swingWindow(), "Nothing to apply",
                                "An application needs an active candidate and an OPEN "
                                        + "vacancy.");
                        return;
                    }
                    ApplicationDialog dialog =
                            new ApplicationDialog(swingWindow(), candidates, vacancies);
                    if (dialog.showDialog() == ApplicationDialog.Result.SAVED) {
                        refreshAll();
                    }
                });
    }

    private void shortlistSelected() {
        var application = selectedApplication();
        if (application == null || !"SUBMITTED".equals(application.getStatus())) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Shortlist",
                "Move application " + application.getApplicationCode() + " ("
                        + application.getCandidateName() + ") to SCREENING?");
        if (!confirmed) {
            return;
        }
        controller.shortlist(application.getId(), this::refreshAll,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void openScheduleDialog(JobApplication fixedApplication) {
        UiThread.executeAsync("Load schedule dialog data",
                () -> new Object[]{
                        ServiceRegistry.recruitmentService().findApplications(null, "SCREENING", null),
                        ServiceRegistry.recruitmentService().findApplications(null, "INTERVIEW", null),
                        ServiceRegistry.employeeService().findAll(
                                new com.ams.hrms.repository.EmployeeRepository.Filter(
                                        "", null, null, null))},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    var screening = (List<JobApplication>) parts[0];
                    @SuppressWarnings("unchecked")
                    var interviewing = (List<JobApplication>) parts[1];
                    @SuppressWarnings("unchecked")
                    var employees = (List<com.ams.hrms.model.Employee>) parts[2];

                    List<JobApplication> eligible = new ArrayList<>();
                    eligible.addAll(screening);
                    eligible.addAll(interviewing);
                    if (fixedApplication != null
                            && eligible.stream().noneMatch(app ->
                                    app.getId() == fixedApplication.getId())) {
                        eligible.add(fixedApplication);
                    }
                    if (eligible.isEmpty()) {
                        Dialogs.info(swingWindow(), "No eligible applications",
                                "Only SCREENING or INTERVIEW applications can be scheduled.");
                        return;
                    }
                    InterviewDialog dialog = InterviewDialog.forScheduling(
                            swingWindow(), eligible, employees, fixedApplication);
                    if (dialog.showDialog() == InterviewDialog.Result.SAVED) {
                        refreshAll();
                    }
                });
    }

    private void recordResult(Interview interview) {
        InterviewDialog dialog = InterviewDialog.forResult(swingWindow(), interview);
        if (dialog.showDialog() == InterviewDialog.Result.SAVED) {
            refreshAll();
        }
    }

    private void createOfferFor(JobApplication application) {
        OfferDialog dialog = new OfferDialog(swingWindow(), application);
        if (dialog.showDialog() == OfferDialog.Result.SAVED) {
            refreshAll();
        }
    }

    private JPopupMenu buildApplicationMenu() {
        JPopupMenu menu = new JPopupMenu();
        var application = selectedApplication();
        boolean manage = SecurityService.can(Permissions.RECRUITMENT_MANAGE);
        boolean interviewManage = SecurityService.can(Permissions.INTERVIEW_MANAGE);
        boolean offerManage = SecurityService.can(Permissions.OFFER_MANAGE);
        String status = application == null ? "" : application.getStatus();

        JMenuItem shortlist = new JMenuItem("Shortlist (to Screening)");
        shortlist.setEnabled("SUBMITTED".equals(status) && manage);
        shortlist.addActionListener(event -> shortlistSelected());

        JMenuItem schedule = new JMenuItem("Schedule Interview");
        schedule.setEnabled(("SCREENING".equals(status) || "INTERVIEW".equals(status))
                && interviewManage);
        schedule.addActionListener(event ->
                openScheduleDialog(selectedApplication()));

        JMenuItem offer = new JMenuItem("Create Offer");
        offer.setEnabled("INTERVIEW".equals(status) && offerManage);
        offer.addActionListener(event -> createOfferFor(selectedApplication()));

        JMenuItem reject = new JMenuItem("Reject...");
        reject.setEnabled(application != null && RecruitmentWorkflow.applicationActive(status)
                && manage);
        reject.addActionListener(event -> rejectSelectedApplication());

        JMenuItem withdraw = new JMenuItem("Withdraw");
        withdraw.setEnabled(application != null && RecruitmentWorkflow.applicationActive(status)
                && manage);
        withdraw.addActionListener(event -> {
            boolean confirmed = Dialogs.confirm(swingWindow(), "Withdraw",
                    "Withdraw application " + application.getApplicationCode() + "?");
            if (!confirmed) {
                return;
            }
            controller.withdrawApplication(application.getId(), this::refreshAll,
                    error -> com.ams.hrms.exception.ErrorHandler.handle(error));
        });

        menu.add(shortlist);
        menu.add(schedule);
        menu.add(offer);
        menu.addSeparator();
        menu.add(reject);
        menu.add(withdraw);
        return menu;
    }

    private void rejectSelectedApplication() {
        var application = selectedApplication();
        if (application == null) {
            return;
        }
        String reason = ReasonDialog.show(swingWindow(), "Reject Application",
                "Reason for rejecting " + application.getApplicationCode() + ":");
        if (reason == null) {
            return;
        }
        controller.rejectApplication(application.getId(), reason, this::refreshAll,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    // ------------------------------------------------------------------
    // Interview actions
    // ------------------------------------------------------------------

    private JPopupMenu buildInterviewMenu() {
        JPopupMenu menu = new JPopupMenu();
        var interview = selectedInterview();
        boolean manage = SecurityService.can(Permissions.INTERVIEW_MANAGE);

        JMenuItem record = new JMenuItem("Record Result...");
        record.setEnabled(interview != null && "PENDING".equals(interview.getResult())
                && manage);
        record.addActionListener(event -> recordResult(selectedInterview()));
        menu.add(record);
        return menu;
    }

    // ------------------------------------------------------------------
    // Offer actions
    // ------------------------------------------------------------------

    private JPopupMenu buildOfferMenu() {
        JPopupMenu menu = new JPopupMenu();
        var offer = selectedOffer();
        boolean manage = SecurityService.can(Permissions.OFFER_MANAGE);
        String status = offer == null ? "" : offer.getStatus();
        boolean hireable = "ACCEPTED".equals(status) && manage && offer.getEmployeeId() == null;

        JMenuItem send = new JMenuItem("Send Offer");
        send.setEnabled("DRAFT".equals(status) && manage);
        send.addActionListener(event -> transitionOffer("SENT"));

        JMenuItem accept = new JMenuItem("Accept Offer");
        accept.setEnabled("SENT".equals(status) && manage);
        accept.addActionListener(event -> transitionOffer("ACCEPTED"));

        JMenuItem decline = new JMenuItem("Decline");
        decline.setEnabled("SENT".equals(status) && manage);
        decline.addActionListener(event -> transitionOffer("DECLINED"));

        JMenuItem expire = new JMenuItem("Mark Expired");
        expire.setEnabled("SENT".equals(status) && manage);
        expire.addActionListener(event -> transitionOffer("EXPIRED"));

        JMenuItem cancel = new JMenuItem("Cancel Offer");
        cancel.setEnabled(("DRAFT".equals(status) || "SENT".equals(status)) && manage);
        cancel.addActionListener(event -> transitionOffer("CANCELLED"));

        JMenuItem hire = new JMenuItem("Hire Candidate");
        hire.setEnabled(hireable);
        hire.addActionListener(event -> hireSelected());

        menu.add(send);
        menu.add(accept);
        menu.addSeparator();
        menu.add(decline);
        menu.add(expire);
        menu.add(cancel);
        menu.addSeparator();
        menu.add(hire);
        return menu;
    }

    private void transitionOffer(String target) {
        var offer = selectedOffer();
        if (offer == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Offer",
                "Set offer " + offer.getOfferCode() + " (" + offer.getCandidateName()
                        + ") to " + target + "?");
        if (!confirmed) {
            return;
        }
        Runnable done = this::refreshAll;
        java.util.function.Consumer<Exception> onError =
                error -> com.ams.hrms.exception.ErrorHandler.handle(error);
        switch (target) {
            case "SENT" -> controller.sendOffer(offer.getId(), done, onError);
            case "ACCEPTED" -> controller.acceptOffer(offer.getId(), done, onError);
            default -> controller.closeOffer(offer.getId(), target, done, onError);
        }
    }

    private void hireSelected() {
        var offer = selectedOffer();
        if (offer == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Hire Candidate",
                "Create an employee from offer " + offer.getOfferCode() + " for "
                        + offer.getCandidateName() + "?"
                        + (offer.getJoiningDate() == null
                                ? "" : " Joining date: " + offer.getJoiningDate() + "."));
        if (!confirmed) {
            return;
        }
        controller.hire(offer.getId(), null, employeeId -> {
            Dialogs.info(swingWindow(), "Hired",
                    "Employee EMP record created (id " + employeeId + ").");
            refreshAll();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}

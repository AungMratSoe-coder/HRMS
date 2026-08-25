package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.model.JobVacancy;

/** Recruitment persistence: vacancies, candidates, applications,
 * interviews and offers (spec section 14). */
public class RecruitmentRepository {

    // ------------------------------------------------------------------
    // Vacancies
    // ------------------------------------------------------------------

    private static final String SELECT_VACANCY =
            "SELECT v.id, v.vacancy_code, v.title, v.department_id, v.position_id, "
                    + "v.headcount, v.employment_type, v.job_description, v.requirements, "
                    + "v.salary_min, v.salary_max, v.opening_date, v.closing_date, v.status, "
                    + "v.created_by, d.dept_name, p.position_name, "
                    + "(SELECT COUNT(*) FROM applications a WHERE a.vacancy_id = v.id "
                    + "    AND a.status = 'ACCEPTED') AS accepted_count "
                    + "FROM job_vacancies v "
                    + "JOIN departments d ON d.id = v.department_id "
                    + "JOIN positions p ON p.id = v.position_id";

    public List<JobVacancy> findVacancies(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SELECT_VACANCY).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (v.title LIKE CONCAT('%', ?, '%') "
                    + "OR v.vacancy_code LIKE CONCAT('%', ?, '%') "
                    + "OR d.dept_name LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND v.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY v.id DESC");
        return new Sql().list(sql.toString(), this::mapVacancy, params.toArray());
    }

    public List<JobVacancy> findOpenVacancies() {
        return new Sql().list(SELECT_VACANCY + " WHERE v.status = 'OPEN' ORDER BY v.id DESC",
                this::mapVacancy);
    }

    public Optional<JobVacancy> findVacancyById(long id) {
        return new Sql().first(SELECT_VACANCY + " WHERE v.id = ?", this::mapVacancy, id);
    }

    public boolean vacancyCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM job_vacancies WHERE vacancy_code = ? "
                        + "AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertVacancy(JobVacancy vacancy) {
        return new Sql().executeInsert(
                "INSERT INTO job_vacancies (vacancy_code, title, department_id, position_id, "
                        + "headcount, employment_type, job_description, requirements, salary_min, "
                        + "salary_max, opening_date, closing_date, status, created_by) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                vacancy.getTitle(), vacancy.getDepartmentId(), vacancy.getPositionId(),
                vacancy.getHeadcount(), vacancy.getEmploymentType(), vacancy.getJobDescription(),
                vacancy.getRequirements(), vacancy.getSalaryMin(), vacancy.getSalaryMax(),
                vacancy.getOpeningDate(), vacancy.getClosingDate(), vacancy.getStatus(),
                vacancy.getCreatedBy());
    }

    public void updateVacancy(JobVacancy vacancy) {
        new Sql().executeUpdate(
                "UPDATE job_vacancies SET title = ?, department_id = ?, position_id = ?, "
                        + "headcount = ?, employment_type = ?, job_description = ?, "
                        + "requirements = ?, salary_min = ?, salary_max = ?, opening_date = ?, "
                        + "closing_date = ? WHERE id = ?",
                vacancy.getTitle(), vacancy.getDepartmentId(), vacancy.getPositionId(),
                vacancy.getHeadcount(), vacancy.getEmploymentType(), vacancy.getJobDescription(),
                vacancy.getRequirements(), vacancy.getSalaryMin(), vacancy.getSalaryMax(),
                vacancy.getOpeningDate(), vacancy.getClosingDate(), vacancy.getId());
    }

    public void updateVacancyStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE job_vacancies SET status = ? WHERE id = ?", status, id);
    }

    public void updateVacancyCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE job_vacancies SET vacancy_code = ? WHERE id = ?", code, id);
    }

    public long openApplicationCount(long vacancyId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM applications WHERE vacancy_id = ? "
                        + "AND status IN ('SUBMITTED', 'SCREENING', 'INTERVIEW', 'OFFER')",
                vacancyId);
    }

    public long acceptedApplicationCount(long vacancyId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM applications WHERE vacancy_id = ? AND status = 'ACCEPTED'",
                vacancyId);
    }

    // ------------------------------------------------------------------
    // Candidates
    // ------------------------------------------------------------------

    private static final String SELECT_CANDIDATE =
            "SELECT c.id, c.candidate_code, c.first_name, c.last_name, c.full_name, "
                    + "c.gender, c.date_of_birth, c.email, c.phone, c.address, c.resume_path, "
                    + "c.skills, c.experience_years, c.expected_salary, c.source, c.status "
                    + "FROM candidates c";

    public List<Candidate> findCandidates(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SELECT_CANDIDATE).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR c.candidate_code LIKE CONCAT('%', ?, '%') "
                    + "OR c.phone LIKE CONCAT('%', ?, '%') "
                    + "OR c.email LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY c.id DESC");
        return new Sql().list(sql.toString(), this::mapCandidate, params.toArray());
    }

    public List<Candidate> findActiveCandidates() {
        return new Sql().list(
                SELECT_CANDIDATE
                        + " WHERE c.status IN ('NEW', 'SHORTLISTED', 'INTERVIEWING') "
                        + "ORDER BY c.id DESC",
                this::mapCandidate);
    }

    public Optional<Candidate> findCandidateById(long id) {
        return new Sql().first(SELECT_CANDIDATE + " WHERE c.id = ?", this::mapCandidate, id);
    }

    public boolean candidateCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM candidates WHERE candidate_code = ? "
                        + "AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertCandidate(Candidate candidate) {
        return new Sql().executeInsert(
                "INSERT INTO candidates (candidate_code, first_name, last_name, gender, "
                        + "date_of_birth, email, phone, address, resume_path, skills, "
                        + "experience_years, expected_salary, source, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                candidate.getFirstName(), candidate.getLastName(), candidate.getGender(),
                candidate.getDateOfBirth(), candidate.getEmail(), candidate.getPhone(),
                candidate.getAddress(), candidate.getResumePath(), candidate.getSkills(),
                candidate.getExperienceYears(), candidate.getExpectedSalary(),
                candidate.getSource(), candidate.getStatus());
    }

    public void updateCandidate(Candidate candidate) {
        new Sql().executeUpdate(
                "UPDATE candidates SET first_name = ?, last_name = ?, gender = ?, "
                        + "date_of_birth = ?, email = ?, phone = ?, address = ?, resume_path = ?, "
                        + "skills = ?, experience_years = ?, expected_salary = ?, source = ? "
                        + "WHERE id = ?",
                candidate.getFirstName(), candidate.getLastName(), candidate.getGender(),
                candidate.getDateOfBirth(), candidate.getEmail(), candidate.getPhone(),
                candidate.getAddress(), candidate.getResumePath(), candidate.getSkills(),
                candidate.getExperienceYears(), candidate.getExpectedSalary(),
                candidate.getSource(), candidate.getId());
    }

    public void updateCandidateStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE candidates SET status = ? WHERE id = ?", status, id);
    }

    public void updateCandidateCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE candidates SET candidate_code = ? WHERE id = ?", code, id);
    }

    // ------------------------------------------------------------------
    // Applications
    // ------------------------------------------------------------------

    private static final String SELECT_APPLICATION =
            "SELECT a.id, a.application_code, a.candidate_id, a.vacancy_id, a.application_date, "
                    + "a.cover_letter, a.status, c.full_name AS candidate_name, "
                    + "c.candidate_code, v.title AS vacancy_title, v.vacancy_code "
                    + "FROM applications a "
                    + "JOIN candidates c ON c.id = a.candidate_id "
                    + "JOIN job_vacancies v ON v.id = a.vacancy_id";

    public List<JobApplication> findApplications(String keyword, String status, Long vacancyId) {
        StringBuilder sql = new StringBuilder(SELECT_APPLICATION).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR a.application_code LIKE CONCAT('%', ?, '%') "
                    + "OR v.title LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status);
        }
        if (vacancyId != null) {
            sql.append(" AND a.vacancy_id = ?");
            params.add(vacancyId);
        }
        sql.append(" ORDER BY a.id DESC");
        return new Sql().list(sql.toString(), this::mapApplication, params.toArray());
    }

    public List<JobApplication> findActiveApplications() {
        return new Sql().list(
                SELECT_APPLICATION
                        + " WHERE a.status IN ('SUBMITTED', 'SCREENING', 'INTERVIEW', 'OFFER') "
                        + "ORDER BY a.id DESC",
                this::mapApplication);
    }

    public Optional<JobApplication> findApplicationById(long id) {
        return new Sql().first(SELECT_APPLICATION + " WHERE a.id = ?",
                this::mapApplication, id);
    }

    public boolean applicationExists(long candidateId, long vacancyId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM applications WHERE candidate_id = ? AND vacancy_id = ?",
                candidateId, vacancyId) > 0;
    }

    public long insertApplication(JobApplication application) {
        return new Sql().executeInsert(
                "INSERT INTO applications (application_code, candidate_id, vacancy_id, "
                        + "application_date, cover_letter, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?)",
                application.getCandidateId(), application.getVacancyId(),
                application.getApplicationDate(), application.getCoverLetter(),
                application.getStatus());
    }

    public void updateApplicationCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE applications SET application_code = ? WHERE id = ?", code, id);
    }

    public void updateApplicationStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE applications SET status = ? WHERE id = ?", status, id);
    }

    // ------------------------------------------------------------------
    // Interviews
    // ------------------------------------------------------------------

    private static final String SELECT_INTERVIEW =
            "SELECT i.id, i.application_id, i.interview_round, i.interview_date, "
                    + "i.interviewer_id, i.mode, i.result, i.score, i.notes, "
                    + "c.full_name AS candidate_name, v.title AS vacancy_title, "
                    + "e.full_name AS interviewer_name "
                    + "FROM interviews i "
                    + "JOIN applications a ON a.id = i.application_id "
                    + "JOIN candidates c ON c.id = a.candidate_id "
                    + "JOIN job_vacancies v ON v.id = a.vacancy_id "
                    + "LEFT JOIN employees e ON e.id = i.interviewer_id";

    public List<Interview> findInterviews(String keyword, String result) {
        StringBuilder sql = new StringBuilder(SELECT_INTERVIEW).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR v.title LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
        }
        if (result != null && !result.isBlank()) {
            sql.append(" AND i.result = ?");
            params.add(result);
        }
        sql.append(" ORDER BY i.interview_date DESC");
        return new Sql().list(sql.toString(), this::mapInterview, params.toArray());
    }

    public List<Interview> findInterviewsForApplication(long applicationId) {
        return new Sql().list(SELECT_INTERVIEW
                        + " WHERE i.application_id = ? ORDER BY i.interview_round",
                this::mapInterview, applicationId);
    }

    public Optional<Interview> findInterviewById(long id) {
        return new Sql().first(SELECT_INTERVIEW + " WHERE i.id = ?", this::mapInterview, id);
    }

    public long insertInterview(Interview interview) {
        return new Sql().executeInsert(
                "INSERT INTO interviews (application_id, interview_round, interview_date, "
                        + "interviewer_id, mode, result, score, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                interview.getApplicationId(), interview.getInterviewRound(),
                interview.getInterviewDate(), interview.getInterviewerId(), interview.getMode(),
                interview.getResult(), interview.getScore(), interview.getNotes());
    }

    public void updateInterviewResult(long id, String result, BigDecimal score, String notes) {
        new Sql().executeUpdate(
                "UPDATE interviews SET result = ?, score = ?, notes = ? WHERE id = ?",
                result, score, notes, id);
    }

    public int nextInterviewRound(long applicationId) {
        return (int) new Sql().scalarLong(
                "SELECT COUNT(*) FROM interviews WHERE application_id = ?", applicationId) + 1;
    }

    /** True when the application has at least one PASSED interview. */
    public boolean hasPassedInterview(long applicationId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM interviews WHERE application_id = ? AND result = 'PASS'",
                applicationId) > 0;
    }

    // ------------------------------------------------------------------
    // Offers
    // ------------------------------------------------------------------

    private static final String SELECT_OFFER =
            "SELECT o.id, o.offer_code, o.application_id, o.position_id, o.offered_salary, "
                    + "o.offer_date, o.expiry_date, o.joining_date, o.status, o.employee_id, "
                    + "c.full_name AS candidate_name, c.candidate_code, p.position_name, "
                    + "a.application_code "
                    + "FROM job_offers o "
                    + "JOIN applications a ON a.id = o.application_id "
                    + "JOIN candidates c ON c.id = a.candidate_id "
                    + "JOIN positions p ON p.id = o.position_id";

    public List<JobOffer> findOffers(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SELECT_OFFER).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR o.offer_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY o.id DESC");
        return new Sql().list(sql.toString(), this::mapOffer, params.toArray());
    }

    public Optional<JobOffer> findOfferById(long id) {
        return new Sql().first(SELECT_OFFER + " WHERE o.id = ?", this::mapOffer, id);
    }

    public boolean offerCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM job_offers WHERE offer_code = ? "
                        + "AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertOffer(JobOffer offer) {
        return new Sql().executeInsert(
                "INSERT INTO job_offers (offer_code, application_id, position_id, "
                        + "offered_salary, offer_date, expiry_date, joining_date, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, ?, ?)",
                offer.getApplicationId(), offer.getPositionId(), offer.getOfferedSalary(),
                offer.getOfferDate(), offer.getExpiryDate(), offer.getJoiningDate(),
                offer.getStatus());
    }

    public void updateOfferCode(long id, String code) {
        new Sql().executeUpdate("UPDATE job_offers SET offer_code = ? WHERE id = ?", code, id);
    }

    public void updateOfferStatus(long id, String status) {
        new Sql().executeUpdate("UPDATE job_offers SET status = ? WHERE id = ?", status, id);
    }

    public void linkOfferEmployee(long id, long employeeId) {
        new Sql().executeUpdate(
                "UPDATE job_offers SET employee_id = ? WHERE id = ?", employeeId, id);
    }

    /** Expires SENT offers whose expiry date has passed. Called at startup. */
    public int expireStaleOffers() {
        return new Sql().executeUpdate(
                "UPDATE job_offers SET status = 'EXPIRED' "
                        + "WHERE status = 'SENT' AND expiry_date IS NOT NULL AND expiry_date < CURDATE()");
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private JobVacancy mapVacancy(ResultSet rs) throws SQLException {
        JobVacancy vacancy = new JobVacancy();
        vacancy.setId(rs.getLong("id"));
        vacancy.setVacancyCode(rs.getString("vacancy_code"));
        vacancy.setTitle(rs.getString("title"));
        vacancy.setDepartmentId(rs.getLong("department_id"));
        vacancy.setPositionId(rs.getLong("position_id"));
        vacancy.setHeadcount(rs.getInt("headcount"));
        vacancy.setEmploymentType(rs.getString("employment_type"));
        vacancy.setJobDescription(rs.getString("job_description"));
        vacancy.setRequirements(rs.getString("requirements"));
        vacancy.setSalaryMin(rs.getBigDecimal("salary_min"));
        vacancy.setSalaryMax(rs.getBigDecimal("salary_max"));
        vacancy.setOpeningDate(rs.getObject("opening_date", LocalDate.class));
        vacancy.setClosingDate(rs.getObject("closing_date", LocalDate.class));
        vacancy.setStatus(rs.getString("status"));
        long createdBy = rs.getLong("created_by");
        vacancy.setCreatedBy(rs.wasNull() ? null : createdBy);
        vacancy.setDepartmentName(rs.getString("dept_name"));
        vacancy.setPositionName(rs.getString("position_name"));
        vacancy.setAcceptedCount(rs.getLong("accepted_count"));
        return vacancy;
    }

    private Candidate mapCandidate(ResultSet rs) throws SQLException {
        Candidate candidate = new Candidate();
        candidate.setId(rs.getLong("id"));
        candidate.setCandidateCode(rs.getString("candidate_code"));
        candidate.setFirstName(rs.getString("first_name"));
        candidate.setLastName(rs.getString("last_name"));
        candidate.setFullName(rs.getString("full_name"));
        candidate.setGender(rs.getString("gender"));
        candidate.setDateOfBirth(rs.getObject("date_of_birth", LocalDate.class));
        candidate.setEmail(rs.getString("email"));
        candidate.setPhone(rs.getString("phone"));
        candidate.setAddress(rs.getString("address"));
        candidate.setResumePath(rs.getString("resume_path"));
        candidate.setSkills(rs.getString("skills"));
        candidate.setExperienceYears(rs.getBigDecimal("experience_years"));
        candidate.setExpectedSalary(rs.getBigDecimal("expected_salary"));
        candidate.setSource(rs.getString("source"));
        candidate.setStatus(rs.getString("status"));
        return candidate;
    }

    private JobApplication mapApplication(ResultSet rs) throws SQLException {
        JobApplication application = new JobApplication();
        application.setId(rs.getLong("id"));
        application.setApplicationCode(rs.getString("application_code"));
        application.setCandidateId(rs.getLong("candidate_id"));
        application.setVacancyId(rs.getLong("vacancy_id"));
        application.setApplicationDate(rs.getObject("application_date", LocalDate.class));
        application.setCoverLetter(rs.getString("cover_letter"));
        application.setStatus(rs.getString("status"));
        application.setCandidateName(rs.getString("candidate_name"));
        application.setCandidateCode(rs.getString("candidate_code"));
        application.setVacancyTitle(rs.getString("vacancy_title"));
        application.setVacancyCode(rs.getString("vacancy_code"));
        return application;
    }

    private Interview mapInterview(ResultSet rs) throws SQLException {
        Interview interview = new Interview();
        interview.setId(rs.getLong("id"));
        interview.setApplicationId(rs.getLong("application_id"));
        interview.setInterviewRound(rs.getInt("interview_round"));
        interview.setInterviewDate(rs.getObject("interview_date", LocalDateTime.class));
        long interviewerId = rs.getLong("interviewer_id");
        interview.setInterviewerId(rs.wasNull() ? null : interviewerId);
        interview.setMode(rs.getString("mode"));
        interview.setResult(rs.getString("result"));
        interview.setScore(rs.getBigDecimal("score"));
        interview.setNotes(rs.getString("notes"));
        interview.setCandidateName(rs.getString("candidate_name"));
        interview.setVacancyTitle(rs.getString("vacancy_title"));
        interview.setInterviewerName(rs.getString("interviewer_name"));
        return interview;
    }

    private JobOffer mapOffer(ResultSet rs) throws SQLException {
        JobOffer offer = new JobOffer();
        offer.setId(rs.getLong("id"));
        offer.setOfferCode(rs.getString("offer_code"));
        offer.setApplicationId(rs.getLong("application_id"));
        offer.setPositionId(rs.getLong("position_id"));
        offer.setOfferedSalary(rs.getBigDecimal("offered_salary"));
        offer.setOfferDate(rs.getObject("offer_date", LocalDate.class));
        offer.setExpiryDate(rs.getObject("expiry_date", LocalDate.class));
        offer.setJoiningDate(rs.getObject("joining_date", LocalDate.class));
        offer.setStatus(rs.getString("status"));
        long employeeId = rs.getLong("employee_id");
        offer.setEmployeeId(rs.wasNull() ? null : employeeId);
        offer.setCandidateName(rs.getString("candidate_name"));
        offer.setCandidateCode(rs.getString("candidate_code"));
        offer.setPositionTitle(rs.getString("position_name"));
        offer.setApplicationCode(rs.getString("application_code"));
        return offer;
    }
}

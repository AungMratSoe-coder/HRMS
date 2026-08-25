package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;

/** Training programs, sessions and enrollments persistence (spec section 23). */
public class TrainingRepository {

    // ------------------------------------------------------------------
    // Programs
    // ------------------------------------------------------------------

    private static final String SELECT_PROGRAM =
            "SELECT p.id, p.program_code, p.program_name, p.description, p.trainer_name, "
                    + "p.cost, p.capacity, p.status, "
                    + "(SELECT COUNT(*) FROM employee_trainings e WHERE e.training_program_id = p.id) "
                    + "AS enrolled_count "
                    + "FROM training_programs p";

    public List<TrainingProgram> findPrograms(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SELECT_PROGRAM).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.program_name LIKE CONCAT('%', ?, '%') "
                    + "OR p.program_code LIKE CONCAT('%', ?, '%') "
                    + "OR p.trainer_name LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.id DESC");
        return new Sql().list(sql.toString(), this::mapProgram, params.toArray());
    }

    public List<TrainingProgram> findLivePrograms() {
        return new Sql().list(SELECT_PROGRAM
                        + " WHERE p.status IN ('PLANNED', 'ONGOING') ORDER BY p.id DESC",
                this::mapProgram);
    }

    public Optional<TrainingProgram> findProgramById(long id) {
        return new Sql().first(SELECT_PROGRAM + " WHERE p.id = ?", this::mapProgram, id);
    }

    public boolean programCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM training_programs WHERE program_code = ? "
                        + "AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertProgram(TrainingProgram program) {
        return new Sql().executeInsert(
                "INSERT INTO training_programs (program_code, program_name, description, "
                        + "trainer_name, cost, capacity, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, ?)",
                program.getName(), program.getDescription(), program.getTrainerName(),
                program.getCost(), program.getCapacity(), program.getStatus());
    }

    public void updateProgramCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE training_programs SET program_code = ? WHERE id = ?", code, id);
    }

    public void updateProgram(TrainingProgram program) {
        new Sql().executeUpdate(
                "UPDATE training_programs SET program_name = ?, description = ?, "
                        + "trainer_name = ?, cost = ?, capacity = ? WHERE id = ?",
                program.getName(), program.getDescription(), program.getTrainerName(),
                program.getCost(), program.getCapacity(), program.getId());
    }

    public void updateProgramStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE training_programs SET status = ? WHERE id = ?", status, id);
    }

    public long enrollmentCount(long programId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_trainings WHERE training_program_id = ?",
                programId);
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    private static final String SELECT_SESSION =
            "SELECT s.id, s.training_program_id, s.start_datetime, s.end_datetime, "
                    + "s.duration_hours, s.location, s.status, p.program_name "
                    + "FROM training_sessions s "
                    + "JOIN training_programs p ON p.id = s.training_program_id";

    public List<TrainingSession> findSessions(Long programId, String status) {
        StringBuilder sql = new StringBuilder(SELECT_SESSION).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (programId != null) {
            sql.append(" AND s.training_program_id = ?");
            params.add(programId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY s.start_datetime DESC");
        return new Sql().list(sql.toString(), this::mapSession, params.toArray());
    }

    /** Live sessions of one program for the enrollment picker. */
    public List<TrainingSession> findLiveSessions(long programId) {
        return new Sql().list(
                SELECT_SESSION + " WHERE s.training_program_id = ? "
                        + "AND s.status IN ('SCHEDULED', 'ONGOING') ORDER BY s.start_datetime",
                this::mapSession, programId);
    }

    public Optional<TrainingSession> findSessionById(long id) {
        return new Sql().first(SELECT_SESSION + " WHERE s.id = ?", this::mapSession, id);
    }

    public long insertSession(TrainingSession session) {
        return new Sql().executeInsert(
                "INSERT INTO training_sessions (training_program_id, start_datetime, "
                        + "end_datetime, duration_hours, location, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                session.getProgramId(), session.getStartDateTime(),
                session.getEndDateTime(), session.getDurationHours(),
                session.getLocation(), session.getStatus());
    }

    public void updateSession(TrainingSession session) {
        new Sql().executeUpdate(
                "UPDATE training_sessions SET start_datetime = ?, end_datetime = ?, "
                        + "duration_hours = ?, location = ? WHERE id = ?",
                session.getStartDateTime(), session.getEndDateTime(),
                session.getDurationHours(), session.getLocation(), session.getId());
    }

    public void updateSessionStatus(long id, String status) {
        new Sql().executeUpdate(
                "UPDATE training_sessions SET status = ? WHERE id = ?", status, id);
    }

    // ------------------------------------------------------------------
    // Enrollments
    // ------------------------------------------------------------------

    private static final String SELECT_ENROLLMENT =
            "SELECT t.id, t.training_program_id, t.session_id, t.employee_id, t.result, "
                    + "t.score, t.certificate_document_id, t.completion_date, t.notes, "
                    + "e.employee_code, e.full_name AS employee_name, p.program_name, "
                    + "s.start_datetime AS session_start, s.location AS session_location "
                    + "FROM employee_trainings t "
                    + "JOIN employees e ON e.id = t.employee_id "
                    + "JOIN training_programs p ON p.id = t.training_program_id "
                    + "LEFT JOIN training_sessions s ON s.id = t.session_id";

    public List<EmployeeTraining> findEnrollments(Long programId, String result,
                                                  String keyword) {
        StringBuilder sql = new StringBuilder(SELECT_ENROLLMENT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (programId != null) {
            sql.append(" AND t.training_program_id = ?");
            params.add(programId);
        }
        if (result != null && !result.isBlank()) {
            sql.append(" AND t.result = ?");
            params.add(result);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR e.employee_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
        }
        sql.append(" ORDER BY t.id DESC");
        return new Sql().list(sql.toString(), this::mapEnrollment, params.toArray());
    }

    /** All enrollments of one employee, newest first (profile view). */
    public List<EmployeeTraining> findEnrollmentsByEmployee(long employeeId) {
        return new Sql().list(SELECT_ENROLLMENT + " WHERE t.employee_id = ? ORDER BY t.id DESC",
                this::mapEnrollment, employeeId);
    }

    public Optional<EmployeeTraining> findEnrollmentById(long id) {
        return new Sql().first(SELECT_ENROLLMENT + " WHERE t.id = ?",
                this::mapEnrollment, id);
    }

    public boolean isEnrolled(long programId, long employeeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_trainings "
                        + "WHERE training_program_id = ? AND employee_id = ?",
                programId, employeeId) > 0;
    }

    public long insertEnrollment(EmployeeTraining enrollment) {
        return new Sql().executeInsert(
                "INSERT INTO employee_trainings (training_program_id, session_id, "
                        + "employee_id, result, notes) VALUES (?, ?, ?, 'ENROLLED', ?)",
                enrollment.getProgramId(), enrollment.getSessionId(),
                enrollment.getEmployeeId(), enrollment.getNotes());
    }

    public void deleteEnrollment(long id) {
        new Sql().executeUpdate(
                "DELETE FROM employee_trainings WHERE id = ? AND result = 'ENROLLED'", id);
    }

    public void recordResult(long id, String result, BigDecimal score,
                             LocalDate completionDate, String notes) {
        new Sql().executeUpdate(
                "UPDATE employee_trainings SET result = ?, score = ?, "
                        + "completion_date = ?, notes = ? WHERE id = ?",
                result, score, completionDate, notes, id);
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private TrainingProgram mapProgram(ResultSet rs) throws SQLException {
        TrainingProgram program = new TrainingProgram();
        program.setId(rs.getLong("id"));
        program.setCode(rs.getString("program_code"));
        program.setName(rs.getString("program_name"));
        program.setDescription(rs.getString("description"));
        program.setTrainerName(rs.getString("trainer_name"));
        program.setCost(rs.getBigDecimal("cost"));
        int capacity = rs.getInt("capacity");
        program.setCapacity(rs.wasNull() ? null : capacity);
        program.setStatus(rs.getString("status"));
        program.setEnrolledCount(rs.getLong("enrolled_count"));
        return program;
    }

    private TrainingSession mapSession(ResultSet rs) throws SQLException {
        TrainingSession session = new TrainingSession();
        session.setId(rs.getLong("id"));
        session.setProgramId(rs.getLong("training_program_id"));
        session.setStartDateTime(rs.getObject("start_datetime", LocalDateTime.class));
        session.setEndDateTime(rs.getObject("end_datetime", LocalDateTime.class));
        session.setDurationHours(rs.getBigDecimal("duration_hours"));
        session.setLocation(rs.getString("location"));
        session.setStatus(rs.getString("status"));
        session.setProgramName(rs.getString("program_name"));
        return session;
    }

    private EmployeeTraining mapEnrollment(ResultSet rs) throws SQLException {
        EmployeeTraining enrollment = new EmployeeTraining();
        enrollment.setId(rs.getLong("id"));
        enrollment.setProgramId(rs.getLong("training_program_id"));
        long sessionId = rs.getLong("session_id");
        enrollment.setSessionId(rs.wasNull() ? null : sessionId);
        enrollment.setEmployeeId(rs.getLong("employee_id"));
        enrollment.setResult(rs.getString("result"));
        enrollment.setScore(rs.getBigDecimal("score"));
        long certificateId = rs.getLong("certificate_document_id");
        enrollment.setCertificateDocumentId(rs.wasNull() ? null : certificateId);
        enrollment.setCompletionDate(rs.getObject("completion_date", LocalDate.class));
        enrollment.setNotes(rs.getString("notes"));
        enrollment.setEmployeeCode(rs.getString("employee_code"));
        enrollment.setEmployeeName(rs.getString("employee_name"));
        enrollment.setProgramName(rs.getString("program_name"));
        LocalDateTime sessionStart = rs.getObject("session_start", LocalDateTime.class);
        String sessionLocation = rs.getString("session_location");
        enrollment.setSessionSummary(sessionStart == null ? null
                : sessionStart.toLocalDate() + (sessionLocation == null
                        ? "" : " @ " + sessionLocation));
        return enrollment;
    }
}

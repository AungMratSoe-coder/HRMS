-- ============================================================================
--  HR Management System - V1__schema.sql
--  Full normalized schema. MySQL >= 8.0.16 (CHECK constraints enforced).
--  Conventions:
--    * BIGINT UNSIGNED auto-increment surrogate primary keys
--    * created_at / updated_at / created_by audit columns on business tables
--    * soft delete via status columns - no physical deletes of business data
--    * money as DECIMAL(12,2), dates/times mapped to java.time in code
--    * utf8mb4 / InnoDB everywhere
-- ============================================================================

CREATE DATABASE IF NOT EXISTS hrms
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE hrms;

-- ============================================================================
--  SECTION 1: SECURITY & AUDIT
-- ============================================================================

CREATE TABLE users (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username             VARCHAR(50)     NOT NULL,
    password_hash        VARCHAR(100)    NOT NULL,
    full_name            VARCHAR(150)    NOT NULL,
    email                VARCHAR(150)    NULL,
    phone                VARCHAR(30)     NULL,
    is_active            TINYINT(1)      NOT NULL DEFAULT 1,
    must_change_password TINYINT(1)      NOT NULL DEFAULT 0,
    last_login_at        DATETIME        NULL,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(30)     NOT NULL COMMENT 'SUPER_ADMIN, HR_MANAGER, ...',
    role_name   VARCHAR(80)     NOT NULL,
    description VARCHAR(255)    NULL,
    is_system   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'system roles cannot be deleted',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    perm_code   VARCHAR(50)     NOT NULL COMMENT 'EMPLOYEE_VIEW, PAYROLL_APPROVE, ...',
    perm_name   VARCHAR(100)    NOT NULL,
    module      VARCHAR(40)     NOT NULL,
    description VARCHAR(255)    NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissions_code (perm_code),
    KEY idx_permissions_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NULL,
    action      VARCHAR(30)     NOT NULL COMMENT 'LOGIN, LOGOUT, CREATE, UPDATE, DELETE, APPROVE, ...',
    module      VARCHAR(50)     NOT NULL,
    entity      VARCHAR(60)     NULL,
    entity_id   BIGINT UNSIGNED NULL,
    description VARCHAR(1000)   NULL,
    ip_address  VARCHAR(45)     NULL,
    device_info VARCHAR(255)    NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_audit_logs_created_at (created_at),
    KEY idx_audit_logs_module (module),
    KEY idx_audit_logs_action (action),
    KEY idx_audit_logs_entity (entity, entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- Append-only by policy: application code never UPDATEs or DELETEs rows.

-- ============================================================================
--  SECTION 2: ORGANIZATION STRUCTURE
-- ============================================================================

CREATE TABLE departments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dept_code   VARCHAR(20)     NOT NULL,
    dept_name   VARCHAR(120)    NOT NULL,
    description VARCHAR(500)    NULL,
    manager_id  BIGINT UNSIGNED NULL COMMENT 'FK added after employees exists',
    status      VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    created_by  BIGINT UNSIGNED NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_departments_code (dept_code),
    UNIQUE KEY uq_departments_name (dept_name),
    CONSTRAINT chk_departments_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_departments_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE positions (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    position_code VARCHAR(20)    NOT NULL,
    position_name VARCHAR(120)   NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    description  VARCHAR(500)    NULL,
    min_salary   DECIMAL(12, 2)  NULL,
    max_salary   DECIMAL(12, 2)  NULL,
    status       VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    created_by   BIGINT UNSIGNED NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_positions_code (position_code),
    UNIQUE KEY uq_positions_dept_name (department_id, position_name),
    CONSTRAINT chk_positions_salary_range CHECK (min_salary IS NULL OR max_salary IS NULL OR min_salary <= max_salary),
    CONSTRAINT chk_positions_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_positions_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_positions_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE employees (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_code   VARCHAR(20)     NOT NULL,
    first_name      VARCHAR(75)     NOT NULL,
    last_name       VARCHAR(75)     NOT NULL,
    full_name       VARCHAR(151)    GENERATED ALWAYS AS (CONCAT(first_name, ' ', last_name)) STORED,
    gender          VARCHAR(10)     NOT NULL,
    date_of_birth   DATE            NULL,
    nrc             VARCHAR(80)     NULL COMMENT 'national identity number, unique when present',
    phone           VARCHAR(30)     NULL,
    email           VARCHAR(150)    NULL,
    address         VARCHAR(300)    NULL,
    photo_path      VARCHAR(400)    NULL,
    join_date       DATE            NOT NULL,
    employment_type VARCHAR(20)     NOT NULL DEFAULT 'FULL_TIME',
    department_id   BIGINT UNSIGNED NULL,
    position_id     BIGINT UNSIGNED NULL,
    manager_id      BIGINT UNSIGNED NULL,
    basic_salary    DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    status          VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employees_code (employee_code),
    UNIQUE KEY uq_employees_nrc (nrc),
    KEY idx_employees_full_name (first_name, last_name),
    KEY idx_employees_department (department_id),
    KEY idx_employees_position (position_id),
    KEY idx_employees_status (status),
    KEY idx_employees_join_date (join_date),
    CONSTRAINT chk_employees_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_employees_employment_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN', 'PROBATION')),
    CONSTRAINT chk_employees_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED')),
    CONSTRAINT chk_employees_basic_salary CHECK (basic_salary >= 0),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT fk_employees_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE departments
    ADD CONSTRAINT fk_departments_manager
        FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE SET NULL;

CREATE TABLE employee_emergency_contacts (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id BIGINT UNSIGNED NOT NULL,
    contact_name VARCHAR(150)   NOT NULL,
    relationship VARCHAR(50)    NULL,
    phone       VARCHAR(30)     NOT NULL,
    address     VARCHAR(300)    NULL,
    is_primary  TINYINT(1)      NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_eec_employee (employee_id),
    CONSTRAINT fk_eec_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE employee_documents (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(30)     NOT NULL,
    file_name     VARCHAR(255)    NOT NULL,
    file_path     VARCHAR(500)    NOT NULL,
    file_size     BIGINT          NULL,
    mime_type     VARCHAR(100)    NULL,
    expiry_date   DATE            NULL,
    notes         VARCHAR(500)    NULL,
    status        VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    uploaded_by   BIGINT UNSIGNED NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_edocs_employee (employee_id),
    KEY idx_edocs_expiry (expiry_date),
    CONSTRAINT chk_edocs_type CHECK (document_type IN ('NRC', 'PASSPORT', 'CONTRACT', 'CERTIFICATE', 'RESUME', 'TRAINING_CERTIFICATE', 'OTHER')),
    CONSTRAINT chk_edocs_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'ARCHIVED', 'DELETED')),
    CONSTRAINT fk_edocs_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_edocs_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- Files live on the file system (app.storage.documents-root); this table holds metadata only.

CREATE TABLE employee_history (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT UNSIGNED NOT NULL,
    change_type    VARCHAR(40)     NOT NULL,
    effective_date DATE            NOT NULL,
    old_value      VARCHAR(500)    NULL,
    new_value      VARCHAR(500)    NULL,
    remarks        VARCHAR(500)    NULL,
    recorded_by    BIGINT UNSIGNED NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ehistory_employee (employee_id, effective_date),
    CONSTRAINT chk_ehistory_type CHECK (change_type IN ('PROMOTION', 'TRANSFER', 'SALARY_CHANGE', 'STATUS_CHANGE', 'DEPARTMENT_CHANGE', 'POSITION_CHANGE', 'SHIFT_CHANGE', 'OTHER')),
    CONSTRAINT fk_ehistory_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ehistory_recorded_by FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- Append-only employment timeline; preserved after resignation/termination.

-- ============================================================================
--  SECTION 3: RECRUITMENT
-- ============================================================================

CREATE TABLE job_vacancies (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    vacancy_code    VARCHAR(20)     NOT NULL,
    title           VARCHAR(150)    NOT NULL,
    department_id   BIGINT UNSIGNED NOT NULL,
    position_id     BIGINT UNSIGNED NOT NULL,
    headcount       INT             NOT NULL DEFAULT 1,
    employment_type VARCHAR(20)     NOT NULL DEFAULT 'FULL_TIME',
    job_description TEXT            NULL,
    requirements    TEXT            NULL,
    salary_min      DECIMAL(12, 2)  NULL,
    salary_max      DECIMAL(12, 2)  NULL,
    opening_date    DATE            NOT NULL,
    closing_date    DATE            NULL,
    status          VARCHAR(15)     NOT NULL DEFAULT 'OPEN',
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_vacancies_code (vacancy_code),
    KEY idx_vacancies_status (status),
    KEY idx_vacancies_position (position_id),
    CONSTRAINT chk_vacancies_headcount CHECK (headcount > 0),
    CONSTRAINT chk_vacancies_employment_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN', 'PROBATION')),
    CONSTRAINT chk_vacancies_status CHECK (status IN ('OPEN', 'ON_HOLD', 'FILLED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT fk_vacancies_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacancies_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vacancies_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE candidates (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_code   VARCHAR(20)     NOT NULL,
    first_name       VARCHAR(75)     NOT NULL,
    last_name        VARCHAR(75)     NOT NULL,
    full_name        VARCHAR(151)    GENERATED ALWAYS AS (CONCAT(first_name, ' ', last_name)) STORED,
    email            VARCHAR(150)    NULL,
    phone            VARCHAR(30)     NOT NULL,
    address          VARCHAR(300)    NULL,
    resume_path      VARCHAR(500)    NULL,
    skills           TEXT            NULL,
    experience_years DECIMAL(4, 1)   NULL,
    expected_salary  DECIMAL(12, 2)  NULL,
    source           VARCHAR(20)     NOT NULL DEFAULT 'OTHER',
    status           VARCHAR(15)     NOT NULL DEFAULT 'NEW',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_candidates_code (candidate_code),
    KEY idx_candidates_status (status),
    KEY idx_candidates_name (first_name, last_name),
    CONSTRAINT chk_candidates_experience CHECK (experience_years IS NULL OR (experience_years >= 0 AND experience_years <= 60)),
    CONSTRAINT chk_candidates_source CHECK (source IN ('WEBSITE', 'REFERRAL', 'AGENCY', 'LINKEDIN', 'JOB_FAIR', 'WALK_IN', 'OTHER')),
    CONSTRAINT chk_candidates_status CHECK (status IN ('NEW', 'SHORTLISTED', 'INTERVIEWING', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE applications (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_code VARCHAR(20)     NOT NULL,
    candidate_id     BIGINT UNSIGNED NOT NULL,
    vacancy_id       BIGINT UNSIGNED NOT NULL,
    application_date DATE            NOT NULL,
    cover_letter     TEXT            NULL,
    status           VARCHAR(15)     NOT NULL DEFAULT 'SUBMITTED',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_applications_code (application_code),
    UNIQUE KEY uq_applications_candidate_vacancy (candidate_id, vacancy_id),
    KEY idx_applications_vacancy (vacancy_id),
    KEY idx_applications_status (status),
    CONSTRAINT chk_applications_status CHECK (status IN ('SUBMITTED', 'SCREENING', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_vacancy FOREIGN KEY (vacancy_id) REFERENCES job_vacancies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE interviews (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id   BIGINT UNSIGNED NOT NULL,
    interview_round  INT             NOT NULL DEFAULT 1,
    interview_date   DATETIME        NOT NULL,
    interviewer_id   BIGINT UNSIGNED NULL,
    mode             VARCHAR(15)     NOT NULL DEFAULT 'IN_PERSON',
    result           VARCHAR(15)     NOT NULL DEFAULT 'PENDING',
    score            DECIMAL(5, 2)   NULL,
    notes            TEXT            NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_interviews_application (application_id),
    KEY idx_interviews_date (interview_date),
    CONSTRAINT chk_interviews_mode CHECK (mode IN ('IN_PERSON', 'PHONE', 'VIDEO')),
    CONSTRAINT chk_interviews_result CHECK (result IN ('PENDING', 'PASS', 'FAIL', 'ON_HOLD')),
    CONSTRAINT chk_interviews_score CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_interviews_interviewer FOREIGN KEY (interviewer_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE job_offers (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    offer_code     VARCHAR(20)     NOT NULL,
    application_id BIGINT UNSIGNED NOT NULL,
    position_id    BIGINT UNSIGNED NOT NULL,
    offered_salary DECIMAL(12, 2)  NOT NULL,
    offer_date     DATE            NOT NULL,
    expiry_date    DATE            NULL,
    joining_date   DATE            NULL,
    status         VARCHAR(15)     NOT NULL DEFAULT 'DRAFT',
    employee_id    BIGINT UNSIGNED NULL COMMENT 'populated when the offer is accepted and hired',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_offers_code (offer_code),
    KEY idx_offers_application (application_id),
    KEY idx_offers_status (status),
    CONSTRAINT chk_offers_salary CHECK (offered_salary >= 0),
    CONSTRAINT chk_offers_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT fk_offers_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_offers_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_offers_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 4: ONBOARDING
-- ============================================================================

CREATE TABLE onboarding_templates (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_name   VARCHAR(150)    NOT NULL,
    description VARCHAR(500)    NULL,
    task_order  INT             NOT NULL DEFAULT 1,
    is_mandatory TINYINT(1)     NOT NULL DEFAULT 1,
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE onboarding_tasks (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id      BIGINT UNSIGNED NOT NULL,
    template_task_id BIGINT UNSIGNED NULL,
    task_name        VARCHAR(150)    NOT NULL,
    task_order       INT             NOT NULL DEFAULT 1,
    due_date         DATE            NULL,
    status           VARCHAR(15)     NOT NULL DEFAULT 'PENDING',
    completed_at     DATETIME        NULL,
    completed_by     BIGINT UNSIGNED NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_obtasks_employee (employee_id, task_order),
    CONSTRAINT chk_obtasks_status CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED', 'WAIVED')),
    CONSTRAINT fk_obtasks_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_obtasks_template FOREIGN KEY (template_task_id) REFERENCES onboarding_templates (id) ON DELETE SET NULL,
    CONSTRAINT fk_obtasks_completed_by FOREIGN KEY (completed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 5: SHIFTS & ATTENDANCE
-- ============================================================================

CREATE TABLE shifts (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shift_code     VARCHAR(20)     NOT NULL,
    shift_name     VARCHAR(80)     NOT NULL,
    start_time     TIME            NOT NULL,
    end_time       TIME            NOT NULL COMMENT 'end < start means the shift crosses midnight',
    grace_minutes  INT             NOT NULL DEFAULT 10,
    break_minutes  INT             NOT NULL DEFAULT 60,
    description    VARCHAR(255)    NULL,
    status         VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_shifts_code (shift_code),
    UNIQUE KEY uq_shifts_name (shift_name),
    CONSTRAINT chk_shifts_grace CHECK (grace_minutes >= 0),
    CONSTRAINT chk_shifts_break CHECK (break_minutes >= 0),
    CONSTRAINT chk_shifts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE employee_shifts (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT UNSIGNED NOT NULL,
    shift_id       BIGINT UNSIGNED NOT NULL,
    effective_from DATE            NOT NULL,
    effective_to   DATE            NULL COMMENT 'NULL = current assignment',
    assigned_by    BIGINT UNSIGNED NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_empshifts_employee_from (employee_id, effective_from),
    KEY idx_empshifts_shift (shift_id),
    KEY idx_empshifts_range (employee_id, effective_from, effective_to),
    CONSTRAINT chk_empshifts_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_empshifts_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_empshifts_shift FOREIGN KEY (shift_id) REFERENCES shifts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_empshifts_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE attendance (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    attendance_date     DATE            NOT NULL,
    check_in            TIME            NULL,
    check_out           TIME            NULL,
    status              VARCHAR(15)     NOT NULL,
    late_minutes        INT             NOT NULL DEFAULT 0,
    early_leave_minutes INT             NOT NULL DEFAULT 0,
    worked_hours        DECIMAL(5, 2)   NULL,
    overtime_hours      DECIMAL(5, 2)   NULL,
    remarks             VARCHAR(255)    NULL,
    corrected_by        BIGINT UNSIGNED NULL,
    correction_reason   VARCHAR(255)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_employee_date (employee_id, attendance_date),
    KEY idx_attendance_date (attendance_date),
    KEY idx_attendance_status (attendance_date, status),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'LATE', 'EARLY_LEAVE', 'HALF_DAY', 'ABSENT', 'LEAVE', 'HOLIDAY', 'WEEKEND', 'MISSION')),
    CONSTRAINT chk_attendance_late CHECK (late_minutes >= 0),
    CONSTRAINT chk_attendance_early CHECK (early_leave_minutes >= 0),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_corrected_by FOREIGN KEY (corrected_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 6: LEAVE MANAGEMENT
-- ============================================================================

CREATE TABLE leave_types (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type_code          VARCHAR(20)     NOT NULL,
    type_name          VARCHAR(80)     NOT NULL,
    annual_quota       DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    is_paid            TINYINT(1)      NOT NULL DEFAULT 1,
    requires_approval  TINYINT(1)      NOT NULL DEFAULT 1,
    carry_forward      TINYINT(1)      NOT NULL DEFAULT 0,
    max_carry_forward  DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    gender_restriction VARCHAR(10)     NULL,
    status             VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_types_code (type_code),
    UNIQUE KEY uq_leave_types_name (type_name),
    CONSTRAINT chk_leave_types_quota CHECK (annual_quota >= 0),
    CONSTRAINT chk_leave_types_gender CHECK (gender_restriction IS NULL OR gender_restriction IN ('MALE', 'FEMALE')),
    CONSTRAINT chk_leave_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE leave_requests (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    leave_code       VARCHAR(20)     NOT NULL,
    employee_id      BIGINT UNSIGNED NOT NULL,
    leave_type_id    BIGINT UNSIGNED NOT NULL,
    start_date       DATE            NOT NULL,
    end_date         DATE            NOT NULL,
    number_of_days   DECIMAL(5, 1)   NOT NULL,
    reason           VARCHAR(500)    NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    decided_by       BIGINT UNSIGNED NULL,
    decided_at       DATETIME        NULL,
    rejection_reason VARCHAR(500)    NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_leave_requests_code (leave_code),
    KEY idx_lr_employee (employee_id, start_date, end_date),
    KEY idx_lr_status (status),
    KEY idx_lr_type (leave_type_id),
    CONSTRAINT chk_lr_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_lr_days CHECK (number_of_days > 0 AND number_of_days <= 366),
    CONSTRAINT chk_lr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_lr_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lr_decided_by FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE leave_approvals (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    leave_request_id  BIGINT UNSIGNED NOT NULL,
    approver_id       BIGINT UNSIGNED NOT NULL,
    approval_level    VARCHAR(15)     NOT NULL,
    decision          VARCHAR(15)     NOT NULL,
    comments          VARCHAR(500)    NULL,
    decided_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_la_request (leave_request_id),
    CONSTRAINT chk_la_level CHECK (approval_level IN ('MANAGER', 'HR')),
    CONSTRAINT chk_la_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT fk_la_request FOREIGN KEY (leave_request_id) REFERENCES leave_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_la_approver FOREIGN KEY (approver_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE leave_balances (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT UNSIGNED NOT NULL,
    leave_type_id   BIGINT UNSIGNED NOT NULL,
    balance_year    SMALLINT        NOT NULL,
    entitled        DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    carried_forward DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    used            DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    pending         DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    adjusted        DECIMAL(5, 1)   NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lb_employee_type_year (employee_id, leave_type_id, balance_year),
    CONSTRAINT chk_lb_nonnegative CHECK (entitled >= 0 AND carried_forward >= 0 AND used >= 0 AND pending >= 0),
    CONSTRAINT fk_lb_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_lb_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- available = entitled + carried_forward + adjusted - used - pending (computed by the service layer)

-- ============================================================================
--  SECTION 7: OVERTIME
-- ============================================================================

CREATE TABLE overtime_requests (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    overtime_code  VARCHAR(20)     NOT NULL,
    employee_id    BIGINT UNSIGNED NOT NULL,
    request_date   DATE            NOT NULL,
    hours          DECIMAL(4, 2)   NOT NULL,
    reason         VARCHAR(500)    NOT NULL,
    rate_per_hour  DECIMAL(12, 2)  NULL COMMENT 'NULL = default rate from settings at approval time',
    amount         DECIMAL(12, 2)  NULL COMMENT 'set on approval: hours * rate',
    status         VARCHAR(15)     NOT NULL DEFAULT 'PENDING',
    approved_by    BIGINT UNSIGNED NULL,
    approved_at    DATETIME        NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ot_code (overtime_code),
    KEY idx_ot_employee (employee_id, request_date),
    KEY idx_ot_status (status),
    CONSTRAINT chk_ot_hours CHECK (hours > 0 AND hours <= 12),
    CONSTRAINT chk_ot_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED')),
    CONSTRAINT fk_ot_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ot_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 8: PAYROLL
-- ============================================================================

CREATE TABLE payroll_periods (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    period_name  VARCHAR(20)     NOT NULL COMMENT 'e.g. 2026-08',
    period_year  SMALLINT        NOT NULL,
    period_month TINYINT         NOT NULL,
    start_date   DATE            NOT NULL,
    end_date     DATE            NOT NULL,
    status       VARCHAR(15)     NOT NULL DEFAULT 'OPEN',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pp_name (period_name),
    UNIQUE KEY uq_pp_year_month (period_year, period_month),
    CONSTRAINT chk_pp_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_pp_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_pp_status CHECK (status IN ('OPEN', 'PROCESSING', 'LOCKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE salary_structures (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT UNSIGNED NOT NULL,
    basic_salary   DECIMAL(12, 2)  NOT NULL,
    currency       VARCHAR(10)     NOT NULL DEFAULT 'USD',
    effective_from DATE            NOT NULL,
    effective_to   DATE            NULL COMMENT 'NULL = current structure',
    created_by     BIGINT UNSIGNED NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ss_employee_from (employee_id, effective_from),
    KEY idx_ss_current (employee_id, effective_to),
    CONSTRAINT chk_ss_salary CHECK (basic_salary >= 0),
    CONSTRAINT chk_ss_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_ss_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ss_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE allowances (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT UNSIGNED NOT NULL,
    allowance_type VARCHAR(40)     NOT NULL,
    name           VARCHAR(120)    NOT NULL,
    amount         DECIMAL(12, 2)  NOT NULL,
    is_taxable     TINYINT(1)      NOT NULL DEFAULT 0,
    recurring      TINYINT(1)      NOT NULL DEFAULT 1,
    effective_from DATE            NOT NULL,
    effective_to   DATE            NULL,
    created_by     BIGINT UNSIGNED NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_allow_employee (employee_id, effective_from, effective_to),
    CONSTRAINT chk_allow_amount CHECK (amount >= 0),
    CONSTRAINT chk_allow_type CHECK (allowance_type IN ('TRANSPORT', 'HOUSING', 'MEAL', 'COMMUNICATION', 'POSITION', 'OTHER')),
    CONSTRAINT fk_allow_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_allow_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE deductions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT UNSIGNED NOT NULL,
    deduction_type  VARCHAR(40)     NOT NULL,
    name            VARCHAR(120)    NOT NULL,
    amount          DECIMAL(12, 2)  NULL COMMENT 'fixed amount when is_percentage = 0',
    is_percentage   TINYINT(1)      NOT NULL DEFAULT 0,
    percentage      DECIMAL(5, 2)   NULL COMMENT 'percent of gross when is_percentage = 1',
    total_installments INT          NULL COMMENT 'for loan-type deductions',
    installments_paid  INT          NOT NULL DEFAULT 0,
    effective_from  DATE            NOT NULL,
    effective_to    DATE            NULL,
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ded_employee (employee_id, effective_from, effective_to),
    CONSTRAINT chk_ded_type CHECK (deduction_type IN ('TAX', 'SOCIAL_SECURITY', 'LOAN', 'ADVANCE', 'OTHER')),
    CONSTRAINT chk_ded_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_ded_percentage CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
    CONSTRAINT fk_ded_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_ded_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE bonuses (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id BIGINT UNSIGNED NOT NULL,
    bonus_type  VARCHAR(30)     NOT NULL,
    name        VARCHAR(120)    NOT NULL,
    amount      DECIMAL(12, 2)  NOT NULL,
    bonus_date  DATE            NOT NULL,
    is_taxable  TINYINT(1)      NOT NULL DEFAULT 1,
    notes       VARCHAR(500)    NULL,
    created_by  BIGINT UNSIGNED NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bonus_employee (employee_id, bonus_date),
    CONSTRAINT chk_bonus_amount CHECK (amount >= 0),
    CONSTRAINT chk_bonus_type CHECK (bonus_type IN ('PERFORMANCE', 'ANNUAL', 'FESTIVAL', 'PROJECT', 'OTHER')),
    CONSTRAINT fk_bonus_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_bonus_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payrolls (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payroll_number     VARCHAR(30)     NOT NULL COMMENT 'e.g. PR-2026-08-EMP-0001',
    employee_id        BIGINT UNSIGNED NOT NULL,
    payroll_period_id  BIGINT UNSIGNED NOT NULL,
    currency           VARCHAR(10)     NOT NULL DEFAULT 'USD',
    basic_salary       DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    total_allowance    DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    total_bonus        DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    total_overtime     DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    gross_salary       DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    tax_amount         DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    social_security    DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    loan_deduction     DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    other_deduction    DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    total_deduction    DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    net_salary         DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    status             VARCHAR(15)     NOT NULL DEFAULT 'DRAFT',
    calculated_at      DATETIME        NULL,
    calculated_by      BIGINT UNSIGNED NULL,
    reviewed_at        DATETIME        NULL,
    reviewed_by        BIGINT UNSIGNED NULL,
    approved_at        DATETIME        NULL,
    approved_by        BIGINT UNSIGNED NULL,
    paid_at            DATETIME        NULL,
    payment_reference  VARCHAR(80)     NULL,
    remarks            VARCHAR(500)    NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_payrolls_number (payroll_number),
    UNIQUE KEY uq_payrolls_employee_period (employee_id, payroll_period_id),
    KEY idx_payrolls_period (payroll_period_id),
    KEY idx_payrolls_status (status),
    CONSTRAINT chk_payrolls_nonneg CHECK (basic_salary >= 0 AND total_allowance >= 0 AND total_bonus >= 0
        AND total_overtime >= 0 AND gross_salary >= 0 AND tax_amount >= 0 AND social_security >= 0
        AND loan_deduction >= 0 AND other_deduction >= 0 AND total_deduction >= 0 AND net_salary >= 0),
    CONSTRAINT chk_payrolls_status CHECK (status IN ('DRAFT', 'CALCULATED', 'REVIEWED', 'APPROVED', 'PAID', 'CANCELLED')),
    CONSTRAINT fk_payrolls_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payrolls_period FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payrolls_calculated_by FOREIGN KEY (calculated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_payrolls_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_payrolls_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- Immutable once APPROVED/PAID; corrections go through a new adjusting payroll record.

CREATE TABLE payroll_items (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payroll_id      BIGINT UNSIGNED NOT NULL,
    item_type       VARCHAR(12)     NOT NULL,
    category        VARCHAR(30)     NOT NULL,
    description     VARCHAR(200)    NOT NULL,
    reference_table VARCHAR(40)     NULL COMMENT 'allowances / deductions / bonuses / overtime_requests',
    reference_id    BIGINT UNSIGNED NULL,
    amount          DECIMAL(12, 2)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pi_payroll (payroll_id),
    CONSTRAINT chk_pi_type CHECK (item_type IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT chk_pi_category CHECK (category IN ('BASIC', 'ALLOWANCE', 'BONUS', 'OVERTIME', 'TAX', 'SOCIAL_SECURITY', 'LOAN', 'OTHER_DEDUCTION')),
    CONSTRAINT chk_pi_amount CHECK (amount >= 0),
    CONSTRAINT fk_pi_payroll FOREIGN KEY (payroll_id) REFERENCES payrolls (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 9: PERFORMANCE
-- ============================================================================

CREATE TABLE performance_criteria (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    criteria_code VARCHAR(20)     NOT NULL,
    criteria_name VARCHAR(100)    NOT NULL,
    weight        DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT 'percent, all active weights should sum to 100',
    description   VARCHAR(500)    NULL,
    is_active     TINYINT(1)      NOT NULL DEFAULT 1,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pc_code (criteria_code),
    CONSTRAINT chk_pc_weight CHECK (weight >= 0 AND weight <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE performance_reviews (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_code       VARCHAR(20)     NOT NULL,
    employee_id       BIGINT UNSIGNED NOT NULL,
    reviewer_id       BIGINT UNSIGNED NULL,
    period_start      DATE            NOT NULL,
    period_end        DATE            NOT NULL,
    overall_score     DECIMAL(4, 2)   NULL COMMENT 'weighted average of item scores (1..5)',
    manager_comments  TEXT            NULL,
    employee_comments TEXT            NULL,
    stage             VARCHAR(20)     NOT NULL DEFAULT 'MANAGER_REVIEW',
    status            VARCHAR(15)     NOT NULL DEFAULT 'DRAFT',
    finalized_at      DATETIME        NULL,
    finalized_by      BIGINT UNSIGNED NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pr_code (review_code),
    UNIQUE KEY uq_pr_employee_period (employee_id, period_start, period_end),
    KEY idx_pr_reviewer (reviewer_id),
    CONSTRAINT chk_pr_dates CHECK (period_end >= period_start),
    CONSTRAINT chk_pr_score CHECK (overall_score IS NULL OR (overall_score >= 1 AND overall_score <= 5)),
    CONSTRAINT chk_pr_stage CHECK (stage IN ('MANAGER_REVIEW', 'EMPLOYEE_FEEDBACK', 'FINALIZED')),
    CONSTRAINT chk_pr_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_pr_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT fk_pr_finalized_by FOREIGN KEY (finalized_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE performance_review_items (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    performance_review_id BIGINT UNSIGNED NOT NULL,
    criteria_id           BIGINT UNSIGNED NOT NULL,
    score                 DECIMAL(3, 1)   NOT NULL,
    comments              VARCHAR(500)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pri_review_criteria (performance_review_id, criteria_id),
    CONSTRAINT chk_pri_score CHECK (score >= 1 AND score <= 5),
    CONSTRAINT fk_pri_review FOREIGN KEY (performance_review_id) REFERENCES performance_reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_pri_criteria FOREIGN KEY (criteria_id) REFERENCES performance_criteria (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 10: TRAINING
-- ============================================================================

CREATE TABLE training_programs (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    program_code VARCHAR(20)     NOT NULL,
    program_name VARCHAR(150)    NOT NULL,
    description  TEXT            NULL,
    trainer_name VARCHAR(150)    NULL,
    cost         DECIMAL(12, 2)  NULL,
    capacity     INT             NULL,
    status       VARCHAR(15)     NOT NULL DEFAULT 'PLANNED',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tp_code (program_code),
    CONSTRAINT chk_tp_cost CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT chk_tp_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT chk_tp_status CHECK (status IN ('PLANNED', 'ONGOING', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE training_sessions (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_program_id BIGINT UNSIGNED NOT NULL,
    start_datetime     DATETIME        NOT NULL,
    end_datetime       DATETIME        NOT NULL,
    duration_hours     DECIMAL(4, 2)   NULL,
    location           VARCHAR(150)    NULL,
    status             VARCHAR(15)     NOT NULL DEFAULT 'SCHEDULED',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ts_program (training_program_id),
    KEY idx_ts_start (start_datetime),
    CONSTRAINT chk_ts_dates CHECK (end_datetime >= start_datetime),
    CONSTRAINT chk_ts_status CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_ts_program FOREIGN KEY (training_program_id) REFERENCES training_programs (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE employee_trainings (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    training_program_id BIGINT UNSIGNED NOT NULL,
    session_id         BIGINT UNSIGNED NULL,
    employee_id        BIGINT UNSIGNED NOT NULL,
    result             VARCHAR(15)     NOT NULL DEFAULT 'ENROLLED',
    score              DECIMAL(5, 2)   NULL,
    certificate_document_id BIGINT UNSIGNED NULL,
    completion_date    DATE            NULL,
    notes              VARCHAR(500)    NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_et_program_employee (training_program_id, employee_id),
    KEY idx_et_employee (employee_id),
    CONSTRAINT chk_et_result CHECK (result IN ('ENROLLED', 'ATTENDED', 'COMPLETED', 'PASSED', 'FAILED', 'NO_SHOW')),
    CONSTRAINT fk_et_program FOREIGN KEY (training_program_id) REFERENCES training_programs (id) ON DELETE CASCADE,
    CONSTRAINT fk_et_session FOREIGN KEY (session_id) REFERENCES training_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_et_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_et_certificate FOREIGN KEY (certificate_document_id) REFERENCES employee_documents (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 11: ASSETS
-- ============================================================================

CREATE TABLE assets (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    asset_code       VARCHAR(20)     NOT NULL,
    asset_name       VARCHAR(150)    NOT NULL,
    category         VARCHAR(20)     NOT NULL,
    serial_number    VARCHAR(100)    NULL,
    purchase_date    DATE            NULL,
    purchase_cost    DECIMAL(12, 2)  NULL,
    warranty_expiry  DATE            NULL,
    condition_status VARCHAR(15)     NOT NULL DEFAULT 'NEW',
    status           VARCHAR(15)     NOT NULL DEFAULT 'AVAILABLE',
    notes            VARCHAR(500)    NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_assets_code (asset_code),
    KEY idx_assets_status (status),
    KEY idx_assets_category (category),
    CONSTRAINT chk_assets_category CHECK (category IN ('LAPTOP', 'DESKTOP', 'MONITOR', 'PHONE', 'TABLET', 'ID_CARD', 'VEHICLE', 'FURNITURE', 'OTHER')),
    CONSTRAINT chk_assets_cost CHECK (purchase_cost IS NULL OR purchase_cost >= 0),
    CONSTRAINT chk_assets_condition CHECK (condition_status IN ('NEW', 'GOOD', 'FAIR', 'POOR', 'DAMAGED')),
    CONSTRAINT chk_assets_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'UNDER_REPAIR', 'RETIRED', 'LOST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE asset_assignments (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    asset_id            BIGINT UNSIGNED NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    assigned_date       DATE            NOT NULL,
    due_return_date     DATE            NULL,
    returned_date       DATE            NULL,
    condition_on_return VARCHAR(15)     NULL,
    notes               VARCHAR(500)    NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'ASSIGNED',
    assigned_by         BIGINT UNSIGNED NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_aa_asset (asset_id),
    KEY idx_aa_employee (employee_id),
    KEY idx_aa_status (status),
    CONSTRAINT chk_aa_dates CHECK (returned_date IS NULL OR returned_date >= assigned_date),
    CONSTRAINT chk_aa_condition CHECK (condition_on_return IS NULL OR condition_on_return IN ('GOOD', 'FAIR', 'POOR', 'DAMAGED')),
    CONSTRAINT chk_aa_status CHECK (status IN ('ASSIGNED', 'RETURNED', 'OVERDUE', 'LOST')),
    CONSTRAINT fk_aa_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_aa_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_aa_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 12: SEPARATION (RESIGNATION / TERMINATION)
-- ============================================================================

CREATE TABLE resignations (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resignation_code    VARCHAR(20)     NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    resignation_date    DATE            NOT NULL,
    last_working_date   DATE            NOT NULL,
    notice_period_days  INT             NOT NULL DEFAULT 30,
    reason              TEXT            NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'SUBMITTED',
    approved_by         BIGINT UNSIGNED NULL,
    approved_at         DATETIME        NULL,
    exit_interview_notes TEXT           NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_resign_code (resignation_code),
    UNIQUE KEY uq_resign_employee_active (employee_id, status),
    KEY idx_resign_dates (last_working_date),
    CONSTRAINT chk_resign_dates CHECK (last_working_date >= resignation_date),
    CONSTRAINT chk_resign_notice CHECK (notice_period_days >= 0),
    CONSTRAINT chk_resign_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'PROCESSED')),
    CONSTRAINT fk_resign_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_resign_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
    -- uq_resign_employee_active allows one open resignation per employee
    -- (multiple PROCESSED rows would violate it; status is set to PROCESSED on
    --  the single active row only - enforced by the service layer).

CREATE TABLE terminations (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    termination_code  VARCHAR(20)     NOT NULL,
    employee_id       BIGINT UNSIGNED NOT NULL,
    termination_date  DATE            NOT NULL,
    reason_category   VARCHAR(20)     NOT NULL,
    reason            TEXT            NULL,
    approved_by       BIGINT UNSIGNED NULL,
    approved_at       DATETIME        NULL,
    eligible_rehire   TINYINT(1)      NOT NULL DEFAULT 1,
    notes             VARCHAR(500)    NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_term_code (termination_code),
    KEY idx_term_employee (employee_id),
    CONSTRAINT chk_term_category CHECK (reason_category IN ('MISCONDUCT', 'PERFORMANCE', 'LAYOFF', 'CONTRACT_END', 'OTHER')),
    CONSTRAINT fk_term_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_term_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
--  SECTION 13: PLATFORM (SETTINGS & NOTIFICATIONS)
-- ============================================================================

CREATE TABLE app_settings (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key  VARCHAR(60)     NOT NULL,
    setting_value TEXT           NULL,
    value_type   VARCHAR(10)     NOT NULL DEFAULT 'STRING',
    category     VARCHAR(30)     NOT NULL DEFAULT 'GENERAL',
    description  VARCHAR(255)    NULL,
    updated_by   BIGINT UNSIGNED NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_settings_key (setting_key),
    CONSTRAINT chk_settings_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN')),
    CONSTRAINT fk_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NULL COMMENT 'NULL = broadcast to all users',
    title            VARCHAR(150)    NOT NULL,
    message          VARCHAR(1000)   NOT NULL,
    notification_type VARCHAR(15)    NOT NULL DEFAULT 'INFO',
    reference_module VARCHAR(50)     NULL,
    reference_id     BIGINT UNSIGNED NULL,
    is_read          TINYINT(1)      NOT NULL DEFAULT 0,
    read_at          DATETIME        NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notif_user_unread (user_id, is_read),
    CONSTRAINT chk_notif_type CHECK (notification_type IN ('INFO', 'WARNING', 'SUCCESS', 'ERROR', 'LEAVE', 'PAYROLL', 'DOCUMENT', 'TRAINING', 'SYSTEM')),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;


-- ============================================================================
--  HR Management System - V2__seed.sql
--  Reference data + development seed.
--
--  DEFAULT DEVELOPMENT LOGIN (created below):
--      username : admin
--      password : Admin@123        (BCrypt-hashed; change after first login)
--
--  Never ship production credentials in this file.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Roles
-- ----------------------------------------------------------------------------
INSERT INTO roles (role_code, role_name, description, is_system) VALUES
    ('SUPER_ADMIN', 'Super Administrator', 'Full unrestricted access to every module and setting.', 1),
    ('HR_MANAGER',  'HR Manager',          'Manages the complete employee lifecycle and approves HR operations.', 1),
    ('HR_OFFICER',  'HR Officer',          'Handles day-to-day HR work: records, attendance, leave, recruitment.', 1),
    ('MANAGER',     'Department Manager',  'Approves team leave/overtime and conducts performance reviews.', 1),
    ('FINANCE',     'Finance',             'Processes, reviews and pays payroll; exports financial reports.', 1),
    ('EMPLOYEE',    'Employee',            'Self-service: own attendance, leave requests, payslips, trainings.', 1);

-- ----------------------------------------------------------------------------
-- Permissions
-- ----------------------------------------------------------------------------
INSERT INTO permissions (perm_code, perm_name, module, description) VALUES
    -- Employees
    ('EMPLOYEE_VIEW',        'View employees',              'EMPLOYEE',   'View employee list and profiles'),
    ('EMPLOYEE_CREATE',      'Create employees',            'EMPLOYEE',   'Register new employees'),
    ('EMPLOYEE_UPDATE',      'Update employees',            'EMPLOYEE',   'Edit employee information'),
    ('EMPLOYEE_DELETE',      'Deactivate employees',        'EMPLOYEE',   'Soft-delete / deactivate employee records'),
    ('EMPLOYEE_PHOTO_UPLOAD','Upload employee photos',      'EMPLOYEE',   'Upload and replace profile photos'),
    ('DOCUMENT_MANAGE',      'Manage documents',            'EMPLOYEE',   'Upload, archive and expire employee documents'),
    -- Departments & positions
    ('DEPARTMENT_VIEW',      'View departments',            'ORG',        'View department list'),
    ('DEPARTMENT_CREATE',    'Create departments',          'ORG',        'Add new departments'),
    ('DEPARTMENT_UPDATE',    'Update departments',          'ORG',        'Edit or deactivate departments'),
    ('POSITION_VIEW',        'View positions',              'ORG',        'View position list'),
    ('POSITION_CREATE',      'Create positions',            'ORG',        'Add new positions'),
    ('POSITION_UPDATE',      'Update positions',            'ORG',        'Edit or deactivate positions'),
    -- Shifts
    ('SHIFT_VIEW',           'View shifts',                 'SHIFT',      'View shift definitions'),
    ('SHIFT_MANAGE',         'Manage shifts',               'SHIFT',      'Create, edit and deactivate shifts'),
    ('SHIFT_ASSIGN',         'Assign shifts',               'SHIFT',      'Assign shifts to employees'),
    -- Attendance
    ('ATTENDANCE_VIEW',      'View attendance',             'ATTENDANCE', 'View attendance records'),
    ('ATTENDANCE_CREATE',    'Record attendance',           'ATTENDANCE', 'Check in / check out / create records'),
    ('ATTENDANCE_UPDATE',    'Correct attendance',          'ATTENDANCE', 'Edit attendance records'),
    ('ATTENDANCE_CORRECTION_APPROVE', 'Approve corrections','ATTENDANCE', 'Approve attendance corrections'),
    -- Leave
    ('LEAVE_VIEW',           'View leaves',                 'LEAVE',      'View leave requests and balances'),
    ('LEAVE_REQUEST',        'Request leave',               'LEAVE',      'Submit leave requests'),
    ('LEAVE_APPROVE',        'Approve/reject leave',        'LEAVE',      'Approve or reject leave requests'),
    ('LEAVE_CANCEL',         'Cancel leave',                'LEAVE',      'Cancel approved leave'),
    -- Overtime
    ('OVERTIME_VIEW',        'View overtime',               'OVERTIME',   'View overtime requests'),
    ('OVERTIME_REQUEST',     'Request overtime',            'OVERTIME',   'Submit overtime requests'),
    ('OVERTIME_APPROVE',     'Approve overtime',            'OVERTIME',   'Approve or reject overtime'),
    -- Payroll
    ('PAYROLL_VIEW',         'View payroll',                'PAYROLL',    'View payroll records'),
    ('PAYROLL_CALCULATE',    'Calculate payroll',           'PAYROLL',    'Run payroll calculation'),
    ('PAYROLL_REVIEW',       'Review payroll',              'PAYROLL',    'Move payroll from calculated to reviewed'),
    ('PAYROLL_APPROVE',      'Approve payroll',             'PAYROLL',    'Approve payroll for payment'),
    ('PAYROLL_MARK_PAID',    'Mark payroll as paid',        'PAYROLL',    'Confirm payments'),
    ('PAYSLIP_VIEW',         'View payslips',               'PAYROLL',    'View payslips'),
    ('PAYSLIP_GENERATE',     'Generate payslips',           'PAYROLL',    'Generate/print/export payslip PDFs'),
    -- Recruitment
    ('RECRUITMENT_VIEW',     'View recruitment',            'RECRUITMENT','View vacancies, candidates, applications'),
    ('RECRUITMENT_MANAGE',   'Manage recruitment',          'RECRUITMENT','Create/edit vacancies and candidates'),
    ('INTERVIEW_MANAGE',     'Manage interviews',           'RECRUITMENT','Schedule interviews and record results'),
    ('OFFER_MANAGE',         'Manage offers',               'RECRUITMENT','Create offers and convert hires'),
    -- Onboarding
    ('ONBOARDING_MANAGE',    'Manage onboarding',           'ONBOARDING', 'Run onboarding checklists'),
    -- Performance
    ('PERFORMANCE_VIEW',     'View reviews',                'PERFORMANCE','View performance reviews'),
    ('PERFORMANCE_MANAGE',   'Conduct reviews',             'PERFORMANCE','Create and finalize performance reviews'),
    -- Training
    ('TRAINING_VIEW',        'View training',               'TRAINING',   'View programs and enrollments'),
    ('TRAINING_MANAGE',      'Manage training',             'TRAINING',   'Create programs, sessions, enrollments'),
    -- Assets
    ('ASSET_VIEW',           'View assets',                 'ASSET',      'View company assets'),
    ('ASSET_ASSIGN',         'Assign assets',               'ASSET',      'Assign/return assets to employees'),
    ('ASSET_MANAGE',         'Manage assets',               'ASSET',      'Register, edit and retire assets'),
    -- Reports
    ('REPORT_VIEW',          'View reports',                'REPORT',     'Run reports'),
    ('REPORT_EXPORT',        'Export reports',              'REPORT',     'Export PDF/Excel, print'),
    -- System
    ('USER_MANAGE',          'Manage users & roles',        'SYSTEM',     'User accounts, role assignment, permissions'),
    ('SETTINGS_MANAGE',      'Manage settings',             'SYSTEM',     'Company and system configuration'),
    ('AUDIT_LOG_VIEW',       'View audit log',              'SYSTEM',     'Read the audit trail');

-- ----------------------------------------------------------------------------
-- Role -> permission grants
-- ----------------------------------------------------------------------------

-- SUPER_ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p WHERE r.role_code = 'SUPER_ADMIN';

-- HR_MANAGER: everything except system administration
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.role_code = 'HR_MANAGER'
  AND p.perm_code NOT IN ('SETTINGS_MANAGE', 'USER_MANAGE');

-- HR_OFFICER: operational HR scope (no payroll approval, no user management)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.role_code = 'HR_OFFICER'
  AND p.perm_code IN (
    'EMPLOYEE_VIEW', 'EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_PHOTO_UPLOAD',
    'DOCUMENT_MANAGE',
    'DEPARTMENT_VIEW', 'DEPARTMENT_UPDATE', 'POSITION_VIEW', 'POSITION_UPDATE',
    'SHIFT_VIEW', 'SHIFT_MANAGE', 'SHIFT_ASSIGN',
    'ATTENDANCE_VIEW', 'ATTENDANCE_CREATE', 'ATTENDANCE_UPDATE', 'ATTENDANCE_CORRECTION_APPROVE',
    'LEAVE_VIEW', 'LEAVE_REQUEST', 'LEAVE_APPROVE', 'LEAVE_CANCEL',
    'OVERTIME_VIEW', 'OVERTIME_REQUEST', 'OVERTIME_APPROVE',
    'PAYROLL_VIEW', 'PAYSLIP_VIEW', 'PAYSLIP_GENERATE',
    'RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE', 'INTERVIEW_MANAGE', 'OFFER_MANAGE',
    'ONBOARDING_MANAGE',
    'PERFORMANCE_VIEW',
    'TRAINING_VIEW', 'TRAINING_MANAGE',
    'ASSET_VIEW', 'ASSET_ASSIGN',
    'REPORT_VIEW', 'REPORT_EXPORT');

-- MANAGER: team leadership scope
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.role_code = 'MANAGER'
  AND p.perm_code IN (
    'EMPLOYEE_VIEW', 'SHIFT_VIEW', 'ATTENDANCE_VIEW',
    'LEAVE_VIEW', 'LEAVE_APPROVE',
    'OVERTIME_VIEW', 'OVERTIME_APPROVE',
    'PERFORMANCE_VIEW', 'PERFORMANCE_MANAGE',
    'TRAINING_VIEW', 'ASSET_VIEW', 'REPORT_VIEW', 'PAYSLIP_VIEW');

-- FINANCE: payroll ownership + reports
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.role_code = 'FINANCE'
  AND p.perm_code IN (
    'EMPLOYEE_VIEW',
    'PAYROLL_VIEW', 'PAYROLL_CALCULATE', 'PAYROLL_REVIEW', 'PAYROLL_APPROVE', 'PAYROLL_MARK_PAID',
    'PAYSLIP_GENERATE',
    'REPORT_VIEW', 'REPORT_EXPORT', 'ASSET_VIEW');

-- EMPLOYEE: self service
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.role_code = 'EMPLOYEE'
  AND p.perm_code IN (
    'EMPLOYEE_VIEW', 'SHIFT_VIEW', 'ATTENDANCE_VIEW',
    'LEAVE_VIEW', 'LEAVE_REQUEST',
    'OVERTIME_VIEW', 'OVERTIME_REQUEST',
    'PAYSLIP_VIEW', 'TRAINING_VIEW', 'PERFORMANCE_VIEW');

-- ----------------------------------------------------------------------------
-- Default administrator
--     username: admin      password: Admin@123
--     The password is stored as a BCrypt hash (cost 12), never in plaintext.
-- ----------------------------------------------------------------------------
INSERT INTO users (username, password_hash, full_name, email, is_active) VALUES
    ('admin', '$2a$12$c.MfEdIBLsfM7kFLk88/zOhQH7SNP4nVDZ0X.p3bt2dbvUNXpCJKK', 'System Administrator', 'admin@ams.local', 1);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';

-- ----------------------------------------------------------------------------
-- Departments
-- ----------------------------------------------------------------------------
INSERT INTO departments (dept_code, dept_name, description, status, created_by) VALUES
    ('ADM', 'Administration',   'Corporate administration and facilities.',        'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('HR',  'Human Resources',  'People operations: hiring, payroll coordination, welfare.', 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('FIN', 'Finance',          'Accounting, payroll processing and budgeting.',   'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('IT',  'Information Technology', 'Software development and IT infrastructure.', 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('OPS', 'Operations',       'Daily business operations and logistics.',        'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('SAL', 'Sales & Marketing','Sales, marketing and customer relations.',        'ACTIVE', (SELECT id FROM users WHERE username = 'admin'));

-- ----------------------------------------------------------------------------
-- Positions
-- ----------------------------------------------------------------------------
INSERT INTO positions (position_code, position_name, department_id, min_salary, max_salary, status, created_by) VALUES
    ('ADM-OFC', 'Administrative Officer', (SELECT id FROM departments WHERE dept_code = 'ADM'), 500.00,  900.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('HR-MGR',  'HR Manager',             (SELECT id FROM departments WHERE dept_code = 'HR'),  1800.00, 2600.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('HR-OFC',  'HR Officer',             (SELECT id FROM departments WHERE dept_code = 'HR'),  700.00,  1400.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('FIN-MGR', 'Finance Manager',        (SELECT id FROM departments WHERE dept_code = 'FIN'), 1800.00, 2600.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('ACC',     'Accountant',             (SELECT id FROM departments WHERE dept_code = 'FIN'), 800.00,  1500.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('IT-MGR',  'IT Manager',             (SELECT id FROM departments WHERE dept_code = 'IT'),  2000.00, 3000.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('IT-DEV',  'Software Developer',     (SELECT id FROM departments WHERE dept_code = 'IT'),  900.00,  2200.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('OPS-MGR', 'Operations Manager',     (SELECT id FROM departments WHERE dept_code = 'OPS'), 1700.00, 2500.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('SAL-EXE', 'Sales Executive',        (SELECT id FROM departments WHERE dept_code = 'SAL'), 600.00,  1600.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin'));

-- ----------------------------------------------------------------------------
-- Sample employees
-- ----------------------------------------------------------------------------
INSERT INTO employees (employee_code, first_name, last_name, gender, date_of_birth, nrc,
                       phone, email, address, join_date, employment_type,
                       department_id, position_id, basic_salary, status, created_by) VALUES
    ('EMP-0001', 'Aung',    'Kyaw',    'MALE',   '1988-04-12', '12/YGN(N)001234',
     '+959770100001', 'aung.kyaw@amsgroup.com.mm',   'No.10, Bahan Township, Yangon',      '2022-01-03', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'IT'),  (SELECT id FROM positions WHERE position_code = 'IT-MGR'),  2500.00, 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('EMP-0002', 'Su Su',   'Hlaing',  'FEMALE', '1990-09-25', '12/YGN(N)005678',
     '+959770100002', 'su.su.hlaing@amsgroup.com.mm', 'No.22, Kamayut Township, Yangon',   '2022-03-01', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'HR'),  (SELECT id FROM positions WHERE position_code = 'HR-MGR'),  2100.00, 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('EMP-0003', 'Kyaw Kyaw', 'Win',   'MALE',   '1995-02-14', '13/YGN(N)009012',
     '+959770100003', 'kyaw.kyaw.win@amsgroup.com.mm', 'No.5, Hlaing Township, Yangon',    '2023-06-15', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'IT'),  (SELECT id FROM positions WHERE position_code = 'IT-DEV'),  1500.00, 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('EMP-0004', 'Thiri',   'Aung',    'FEMALE', '1992-11-30', '12/YGN(N)011345',
     '+959770100004', 'thiri.aung@amsgroup.com.mm',   'No.88, Sanchaung Township, Yangon', '2022-07-01', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'FIN'), (SELECT id FROM positions WHERE position_code = 'FIN-MGR'), 2200.00, 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('EMP-0005', 'Myint Mo','Tun',     'FEMALE', '1996-06-08', '14/YGN(N)015678',
     '+959770100005', 'myint.mo.tun@amsgroup.com.mm', 'No.31, Insein Township, Yangon',    '2024-02-01', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'HR'),  (SELECT id FROM positions WHERE position_code = 'HR-OFC'),  1000.00, 'ACTIVE', (SELECT id FROM users WHERE username = 'admin')),
    ('EMP-0006', 'Zaw Zaw', 'Lwin',    'MALE',   '1993-08-19', '13/YGN(N)018901',
     '+959770100006', 'zaw.zaw.lwin@amsgroup.com.mm', 'No.47, Mayangone Township, Yangon', '2023-10-16', 'FULL_TIME',
     (SELECT id FROM departments WHERE dept_code = 'SAL'), (SELECT id FROM positions WHERE position_code = 'SAL-EXE'), 950.00,  'ACTIVE', (SELECT id FROM users WHERE username = 'admin'));

-- Department managers
UPDATE departments SET manager_id = (SELECT id FROM employees WHERE employee_code = 'EMP-0001') WHERE dept_code = 'IT';
UPDATE departments SET manager_id = (SELECT id FROM employees WHERE employee_code = 'EMP-0002') WHERE dept_code = 'HR';
UPDATE departments SET manager_id = (SELECT id FROM employees WHERE employee_code = 'EMP-0004') WHERE dept_code = 'FIN';
UPDATE departments SET manager_id = (SELECT id FROM employees WHERE employee_code = 'EMP-0006') WHERE dept_code = 'SAL';

-- Team hierarchy
-- (subselect wrapped in a derived table because MySQL forbids selecting from
--  the update target directly - error 1093)
UPDATE employees
SET manager_id = (
    SELECT t.id FROM (SELECT id, employee_code FROM employees) t
    WHERE t.employee_code = 'EMP-0001')
WHERE employee_code = 'EMP-0003';

UPDATE employees
SET manager_id = (
    SELECT t.id FROM (SELECT id, employee_code FROM employees) t
    WHERE t.employee_code = 'EMP-0002')
WHERE employee_code = 'EMP-0005';

-- Salary structures
INSERT INTO salary_structures (employee_id, basic_salary, currency, effective_from, effective_to, created_by)
SELECT e.id, e.basic_salary, 'USD', e.join_date, NULL, (SELECT id FROM users WHERE username = 'admin')
FROM employees e;

-- Recurring allowances
INSERT INTO allowances (employee_id, allowance_type, name, amount, is_taxable, recurring, effective_from, created_by)
SELECT e.id, 'TRANSPORT', 'Transport Allowance', 50.00, 0, 1, e.join_date,
       (SELECT id FROM users WHERE username = 'admin')
FROM employees e WHERE e.status = 'ACTIVE';

INSERT INTO allowances (employee_id, allowance_type, name, amount, is_taxable, recurring, effective_from, created_by)
SELECT e.id, 'HOUSING', 'Housing Allowance', 120.00, 1, 1, e.join_date,
       (SELECT id FROM users WHERE username = 'admin')
FROM employees e WHERE e.employee_code IN ('EMP-0001', 'EMP-0002');

-- ----------------------------------------------------------------------------
-- Leave types
-- ----------------------------------------------------------------------------
INSERT INTO leave_types (type_code, type_name, annual_quota, is_paid, requires_approval, carry_forward, max_carry_forward, gender_restriction, status) VALUES
    ('ANNUAL',    'Annual Leave',    18.0, 1, 1, 1, 5.0, NULL,     'ACTIVE'),
    ('SICK',      'Sick Leave',      14.0, 1, 1, 0, 0.0, NULL,     'ACTIVE'),
    ('CASUAL',    'Casual Leave',     7.0, 1, 1, 0, 0.0, NULL,     'ACTIVE'),
    ('MATERNITY', 'Maternity Leave', 90.0, 1, 1, 0, 0.0, 'FEMALE', 'ACTIVE'),
    ('PATERNITY', 'Paternity Leave', 15.0, 1, 1, 0, 0.0, 'MALE',   'ACTIVE'),
    ('UNPAID',    'Unpaid Leave',    30.0, 0, 1, 0, 0.0, NULL,     'ACTIVE'),
    ('OTHER',     'Other Leave',      5.0, 0, 1, 0, 0.0, NULL,     'ACTIVE');

-- Opening leave balances for all active employees and all leave types
INSERT INTO leave_balances (employee_id, leave_type_id, balance_year, entitled, carried_forward, used, pending, adjusted)
SELECT e.id, lt.id, YEAR(CURDATE()), lt.annual_quota, 0, 0, 0, 0
FROM employees e CROSS JOIN leave_types lt
WHERE e.status = 'ACTIVE';

-- ----------------------------------------------------------------------------
-- Shifts
-- ----------------------------------------------------------------------------
INSERT INTO shifts (shift_code, shift_name, start_time, end_time, grace_minutes, break_minutes, description, status) VALUES
    ('SH-MORNING', 'Morning Shift',  '08:00:00', '17:00:00', 15, 60, 'Standard office hours.',        'ACTIVE'),
    ('SH-EVENING', 'Evening Shift',  '16:00:00', '00:00:00', 15, 30, 'Afternoon shift crossing midnight.', 'ACTIVE'),
    ('SH-NIGHT',   'Night Shift',    '23:00:00', '07:00:00', 15, 30, 'Overnight shift.',              'ACTIVE'),
    ('SH-FLEX',    'Flexible Hours', '09:00:00', '18:00:00', 20, 45, 'Flexible core hours.',          'ACTIVE');

INSERT INTO employee_shifts (employee_id, shift_id, effective_from, assigned_by)
SELECT e.id, (SELECT id FROM shifts WHERE shift_code = 'SH-MORNING'), e.join_date,
       (SELECT id FROM users WHERE username = 'admin')
FROM employees e WHERE e.status = 'ACTIVE';

-- ----------------------------------------------------------------------------
-- Performance criteria (weights sum to 100)
-- ----------------------------------------------------------------------------
INSERT INTO performance_criteria (criteria_code, criteria_name, weight, description, is_active) VALUES
    ('PROD', 'Productivity',      20.00, 'Volume and efficiency of work delivered.',        1),
    ('QUAL', 'Quality of Work',   20.00, 'Accuracy, completeness and standards.',           1),
    ('COMM', 'Communication',     10.00, 'Clarity, listening and information sharing.',     1),
    ('TEAM', 'Teamwork',          10.00, 'Collaboration and support of colleagues.',        1),
    ('ATT',  'Attendance',        10.00, 'Punctuality and presence record.',                1),
    ('LEAD', 'Leadership',        10.00, 'Initiative, guidance and ownership.',             1),
    ('TECH', 'Technical Skills',  20.00, 'Job-specific technical competence.',              1);

-- ----------------------------------------------------------------------------
-- Onboarding checklist template
-- ----------------------------------------------------------------------------
INSERT INTO onboarding_templates (task_name, description, task_order, is_mandatory, is_active) VALUES
    ('Create employee profile',      'Register the new hire in the system.',                    1,  1, 1),
    ('Sign employment contract',     'Collect signed employment contract.',                     2,  1, 1),
    ('Collect national ID copy',     'File NRC / national ID copy.',                            3,  1, 1),
    ('Collect other documents',      'Certificates, references and other paperwork.',           4,  1, 1),
    ('Assign department & position', 'Confirm reporting line and job title.',                   5,  1, 1),
    ('Set up salary structure',      'Record agreed basic salary and allowances.',              6,  1, 1),
    ('Assign shift',                 'Set working schedule for the new hire.',                  7,  1, 1),
    ('Issue company assets',         'Laptop, ID card and other equipment.',                    8,  0, 1),
    ('Attend orientation',           'Company introduction session.',                           9,  1, 1),
    ('Create system account',        'Provide HRMS and email accounts.',                        10, 1, 1);

-- ----------------------------------------------------------------------------
-- Sample assets
-- ----------------------------------------------------------------------------
INSERT INTO assets (asset_code, asset_name, category, serial_number, purchase_date, purchase_cost, condition_status, status) VALUES
    ('AST-0001', 'Dell Latitude 5440 Laptop', 'LAPTOP',  'DL5440-2025-001', '2025-03-15', 950.00,  'NEW',  'AVAILABLE'),
    ('AST-0002', 'HP EliteDesk 800 G9',       'DESKTOP', 'HPED800-2024-017','2024-08-02', 780.00,  'GOOD', 'AVAILABLE'),
    ('AST-0003', 'Dell P2422H Monitor',       'MONITOR', 'DLP2422-2024-042','2024-08-02', 190.00,  'GOOD', 'AVAILABLE'),
    ('AST-0004', 'iPhone 13',                 'PHONE',   'IPH13-2023-009',  '2023-05-20', 640.00,  'FAIR', 'AVAILABLE'),
    ('AST-0005', 'iPad Air 5',                'TABLET',  'IPAIR5-2024-004', '2024-01-10', 520.00,  'NEW',  'AVAILABLE'),
    ('AST-0006', 'Toyota Hilux Double Cab',   'VEHICLE', 'THX-2022-771',    '2022-11-05', 45000.00,'GOOD', 'AVAILABLE');

-- ----------------------------------------------------------------------------
-- Application settings
-- ----------------------------------------------------------------------------
INSERT INTO app_settings (setting_key, setting_value, value_type, category, description, updated_by) VALUES
    ('company.name',                        'AMS Group of Companies',            'STRING',  'COMPANY',    'Registered company name shown on reports and payslips.', (SELECT id FROM users WHERE username = 'admin')),
    ('company.address',                     'No.123, Pyay Road, Yangon, Myanmar','STRING',  'COMPANY',    'Head office address.',                                   (SELECT id FROM users WHERE username = 'admin')),
    ('company.phone',                       '+95 1 234 5678',                    'STRING',  'COMPANY',    'Main office phone.',                                     (SELECT id FROM users WHERE username = 'admin')),
    ('company.email',                       'hr@amsgroup.com.mm',                'STRING',  'COMPANY',    'HR contact email.',                                      (SELECT id FROM users WHERE username = 'admin')),
    ('company.logo_path',                   '',                                  'STRING',  'COMPANY',    'Path to the company logo image file.',                   (SELECT id FROM users WHERE username = 'admin')),
    ('payroll.currency',                    'USD',                               'STRING',  'PAYROLL',    'Currency code used for salaries and payslips.',          (SELECT id FROM users WHERE username = 'admin')),
    ('payroll.overtime_rate_multiplier',    '1.5',                               'NUMBER',  'PAYROLL',    'Default overtime multiplier applied to hourly base rate.',(SELECT id FROM users WHERE username = 'admin')),
    ('payroll.tax_rate_percent',            '5',                                 'NUMBER',  'PAYROLL',    'Default income tax percentage applied at calculation.',  (SELECT id FROM users WHERE username = 'admin')),
    ('payroll.social_security_employee_percent', '2',                            'NUMBER',  'PAYROLL',    'Employee social security contribution percent.',         (SELECT id FROM users WHERE username = 'admin')),
    ('payroll.social_security_employer_percent', '3',                            'NUMBER',  'PAYROLL',    'Employer social security contribution percent.',         (SELECT id FROM users WHERE username = 'admin')),
    ('payroll.working_days_per_month',      '22',                                'NUMBER',  'PAYROLL',    'Standard working days used for daily-rate calculations.',(SELECT id FROM users WHERE username = 'admin')),
    ('attendance.default_shift_code',       'SH-MORNING',                        'STRING',  'ATTENDANCE', 'Shift assumed when an employee has no assignment.',      (SELECT id FROM users WHERE username = 'admin')),
    ('leave.carry_forward_enabled',         'true',                              'BOOLEAN', 'LEAVE',      'Whether unused annual leave carries into the next year.',(SELECT id FROM users WHERE username = 'admin')),
    ('documents.expiry_warning_days',       '30',                                'NUMBER',  'DOCUMENTS',  'Days before document expiry to raise a notification.',   (SELECT id FROM users WHERE username = 'admin')),
    ('app.timezone',                        'Asia/Yangon',                       'STRING',  'GENERAL',    'Business timezone for attendance calculations.',         (SELECT id FROM users WHERE username = 'admin'));

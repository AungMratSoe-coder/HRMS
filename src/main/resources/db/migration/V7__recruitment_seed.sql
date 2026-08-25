-- ============================================================================
--  V7: Recruitment demo seed
--  The recruitment tables shipped without sample rows, so the module rendered
--  an empty table out of the box. Seeds a small, coherent hiring pipeline:
--  four open vacancies, six candidates at different stages, applications,
--  a couple of interviews and one sent offer.
-- ============================================================================

-- Vacancies (positions/departments from the V2 seed)
INSERT INTO job_vacancies (vacancy_code, title, department_id, position_id, headcount,
                           employment_type, job_description, requirements,
                           salary_min, salary_max, opening_date, closing_date, status, created_by)
VALUES
    ('VAC-0001', 'Software Developer', (SELECT id FROM departments WHERE dept_code = 'IT'),
     (SELECT id FROM positions WHERE position_code = 'IT-DEV'), 2, 'FULL_TIME',
     'Build and maintain internal Java desktop applications and services.',
     'Java experience, SQL basics, teamwork.', 1200.00, 2000.00,
     CURDATE() - INTERVAL 30 DAY, CURDATE() + INTERVAL 30 DAY, 'OPEN',
     (SELECT id FROM users WHERE username = 'admin')),
    ('VAC-0002', 'HR Officer', (SELECT id FROM departments WHERE dept_code = 'HR'),
     (SELECT id FROM positions WHERE position_code = 'HR-OFC'), 1, 'FULL_TIME',
     'Support daily HR operations: records, leave, onboarding.',
     'HR fundamentals, communication, discretion.', 800.00, 1200.00,
     CURDATE() - INTERVAL 20 DAY, CURDATE() + INTERVAL 20 DAY, 'OPEN',
     (SELECT id FROM users WHERE username = 'admin')),
    ('VAC-0003', 'Accountant', (SELECT id FROM departments WHERE dept_code = 'FIN'),
     (SELECT id FROM positions WHERE position_code = 'ACC'), 1, 'FULL_TIME',
     'Bookkeeping, payroll preparation support and reporting.',
     'Accounting degree, Excel, attention to detail.', 900.00, 1400.00,
     CURDATE() - INTERVAL 15 DAY, CURDATE() + INTERVAL 45 DAY, 'OPEN',
     (SELECT id FROM users WHERE username = 'admin')),
    ('VAC-0004', 'Sales Executive', (SELECT id FROM departments WHERE dept_code = 'SAL'),
     (SELECT id FROM positions WHERE position_code = 'SAL-EXE'), 3, 'FULL_TIME',
     'Prospect and onboard new customers; own the sales pipeline.',
     'Sales experience, driving licence.', 600.00, 1000.00,
     CURDATE() - INTERVAL 10 DAY, NULL, 'OPEN',
     (SELECT id FROM users WHERE username = 'admin'));

-- Candidates across the pipeline stages
INSERT INTO candidates (candidate_code, first_name, last_name, gender, date_of_birth,
                        email, phone, address, skills, experience_years, expected_salary,
                        source, status)
VALUES
    ('CAN-0001', 'Min', 'Thu', 'MALE', '1996-03-14', 'min.thu@mail.com', '+959770200001',
     'Yangon', 'Java, MySQL, Git', 3.0, 1500.00, 'LINKEDIN', 'INTERVIEWING'),
    ('CAN-0002', 'Hnin', 'Yu', 'FEMALE', '1998-07-02', 'hnin.yu@mail.com', '+959770200002',
     'Yangon', 'HR administration, Myanmar labour law basics', 2.0, 1000.00, 'WEBSITE', 'SHORTLISTED'),
    ('CAN-0003', 'Kyaw', 'Swin', 'MALE', '1994-11-21', 'kyaw.swin@mail.com', '+959770200003',
     'Mandalay', 'Bookkeeping, Excel, AccSys', 4.5, 1300.00, 'REFERRAL', 'OFFERED'),
    ('CAN-0004', 'Thet', 'Naing', 'MALE', '1999-01-30', 'thet.naing@mail.com', '+959770200004',
     'Yangon', 'Sales, customer service', 1.5, 800.00, 'JOB_FAIR', 'NEW'),
    ('CAN-0005', 'Su', 'Myat', 'FEMALE', '1997-05-17', 'su.myat@mail.com', '+959770200005',
     'Yangon', 'Java, Spring Boot', 2.5, 1600.00, 'LINKEDIN', 'REJECTED'),
    ('CAN-0006', 'Aung', 'Ko', 'MALE', '1995-09-09', 'aung.ko@mail.com', '+959770200006',
     'Bago', 'Sales, negotiation', 3.0, 900.00, 'WALK_IN', 'NEW');

-- Applications linking candidates to vacancies
INSERT INTO applications (application_code, candidate_id, vacancy_id, application_date,
                          cover_letter, status)
VALUES
    ('APP-0001', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0001'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0001'),
     CURDATE() - INTERVAL 18 DAY, 'Passionate about clean code and desktop UX.', 'INTERVIEW'),
    ('APP-0002', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0002'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0002'),
     CURDATE() - INTERVAL 12 DAY, 'Two years of HR support experience.', 'SCREENING'),
    ('APP-0003', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0003'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0003'),
     CURDATE() - INTERVAL 10 DAY, 'Experienced bookkeeper ready to own the ledger.', 'OFFER'),
    ('APP-0004', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0004'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0004'),
     CURDATE() - INTERVAL 5 DAY, 'Eager to grow in sales.', 'SUBMITTED'),
    ('APP-0005', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0005'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0001'),
     CURDATE() - INTERVAL 22 DAY, 'Backend focus, strong Spring fundamentals.', 'REJECTED'),
    ('APP-0006', (SELECT id FROM candidates WHERE candidate_code = 'CAN-0006'),
     (SELECT id FROM job_vacancies WHERE vacancy_code = 'VAC-0004'),
     CURDATE() - INTERVAL 3 DAY, 'Three years of field sales.', 'SUBMITTED');

-- Interviews: one passed, one upcoming
INSERT INTO interviews (application_id, interview_round, interview_date, interviewer_id,
                        mode, result, score, notes)
VALUES
    ((SELECT id FROM applications WHERE application_code = 'APP-0001'), 1,
     NOW() - INTERVAL 5 DAY,
     (SELECT id FROM employees WHERE employee_code = 'EMP-0001'),
     'IN_PERSON', 'PASS', 82.00, 'Strong Java fundamentals; good problem solving.'),
    ((SELECT id FROM applications WHERE application_code = 'APP-0001'), 2,
     NOW() + INTERVAL 3 DAY,
     (SELECT id FROM employees WHERE employee_code = 'EMP-0001'),
     'VIDEO', 'PENDING', NULL, 'Technical deep-dive with the IT manager.');

-- One offer sent for the accountant role
INSERT INTO job_offers (offer_code, application_id, position_id, offered_salary, offer_date,
                        expiry_date, joining_date, status)
VALUES
    ('OFF-0001', (SELECT id FROM applications WHERE application_code = 'APP-0003'),
     (SELECT id FROM positions WHERE position_code = 'ACC'),
     1250.00, CURDATE() - INTERVAL 2 DAY, CURDATE() + INTERVAL 12 DAY,
     CURDATE() + INTERVAL 30 DAY, 'SENT');

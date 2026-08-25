-- ----------------------------------------------------------------------------
-- Demo data for the Training and Performance modules. These tables had no
-- seed rows (V2 only seeds performance_criteria), so both module tables
-- rendered empty.
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- Training: programs, sessions, enrollments
-- ----------------------------------------------------------------------------
INSERT INTO training_programs (program_code, program_name, description, trainer_name, cost, capacity, status) VALUES
    ('TRN-001', 'Java Fundamentals',           'Core Java, OOP and collections bootcamp for developers.', 'U Aung Tech',     300.00, 10, 'COMPLETED'),
    ('TRN-002', 'Workplace Safety',            'Office and fire safety, first-aid basics.',               'Daw Hla Hla',     150.00, 25, 'ONGOING'),
    ('TRN-003', 'Leadership Essentials',       'Coaching, feedback and team leadership skills.',          'U Min Min',       400.00, 8,  'PLANNED'),
    ('TRN-004', 'Customer Service Excellence', 'Customer handling and complaint resolution.',             'Daw May May',     200.00, 15, 'PLANNED');

INSERT INTO training_sessions (training_program_id, start_datetime, end_datetime, duration_hours, location, status)
SELECT id, '2026-05-04 09:00:00', '2026-05-06 16:00:00', 18.00, 'Training Room A', 'COMPLETED'
FROM training_programs WHERE program_code = 'TRN-001';

INSERT INTO training_sessions (training_program_id, start_datetime, end_datetime, duration_hours, location, status)
SELECT id, '2026-08-20 09:00:00', '2026-08-21 16:00:00', 12.00, 'Training Room B', 'ONGOING'
FROM training_programs WHERE program_code = 'TRN-002';

INSERT INTO training_sessions (training_program_id, start_datetime, end_datetime, duration_hours, location, status)
SELECT id, '2026-09-10 09:00:00', '2026-09-11 16:00:00', 12.00, 'Training Room A', 'SCHEDULED'
FROM training_programs WHERE program_code = 'TRN-003';

INSERT INTO training_sessions (training_program_id, start_datetime, end_datetime, duration_hours, location, status)
SELECT id, '2026-10-05 09:00:00', '2026-10-05 17:00:00', 7.00, 'Conference Room', 'SCHEDULED'
FROM training_programs WHERE program_code = 'TRN-004';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, s.id, e.id, 'PASSED', 88.00, '2026-05-06', 'Top scorer of the batch.'
FROM training_programs p
JOIN training_sessions s ON s.training_program_id = p.id
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0001') e
WHERE p.program_code = 'TRN-001';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, s.id, e.id, 'COMPLETED', 76.50, '2026-05-06', NULL
FROM training_programs p
JOIN training_sessions s ON s.training_program_id = p.id
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0003') e
WHERE p.program_code = 'TRN-001';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, s.id, e.id, 'ATTENDED', NULL, NULL, 'Missed the final assessment.'
FROM training_programs p
JOIN training_sessions s ON s.training_program_id = p.id
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0004') e
WHERE p.program_code = 'TRN-001';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, s.id, e.id, 'ENROLLED', NULL, NULL, NULL
FROM training_programs p
JOIN training_sessions s ON s.training_program_id = p.id
JOIN (SELECT id FROM employees WHERE employee_code IN ('EMP-0002', 'EMP-0005', 'EMP-0006')) e
WHERE p.program_code = 'TRN-002';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, NULL, e.id, 'ENROLLED', NULL, NULL, NULL
FROM training_programs p
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0002') e
WHERE p.program_code = 'TRN-003';

INSERT INTO employee_trainings (training_program_id, session_id, employee_id, result, score, completion_date, notes)
SELECT p.id, NULL, e.id, 'ENROLLED', NULL, NULL, NULL
FROM training_programs p
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0009') e
WHERE p.program_code = 'TRN-004';

-- ----------------------------------------------------------------------------
-- Performance: reviews and scored items (criteria weights from V2 seed)
-- ----------------------------------------------------------------------------
INSERT INTO performance_reviews (review_code, employee_id, reviewer_id, period_start, period_end,
                                 overall_score, manager_comments, employee_comments,
                                 stage, status, finalized_at, finalized_by)
SELECT 'PRV-0001', e.id, r.id, '2026-01-01', '2026-06-30', 4.15,
       'Consistently delivers high-quality work and mentors juniors.',
       'Happy with the team support during the migration project.',
       'FINALIZED', 'COMPLETED', NOW(), (SELECT id FROM users WHERE username = 'admin')
FROM (SELECT id FROM employees WHERE employee_code = 'EMP-0001') e
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0002') r;

INSERT INTO performance_reviews (review_code, employee_id, reviewer_id, period_start, period_end,
                                 overall_score, manager_comments, employee_comments,
                                 stage, status, finalized_at, finalized_by)
SELECT 'PRV-0002', e.id, r.id, '2026-01-01', '2026-06-30', 3.50,
       'Solid contributor; needs to sharpen technical depth.',
       'Would like more exposure to new technologies.',
       'FINALIZED', 'COMPLETED', NOW(), (SELECT id FROM users WHERE username = 'admin')
FROM (SELECT id FROM employees WHERE employee_code = 'EMP-0003') e
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0001') r;

INSERT INTO performance_reviews (review_code, employee_id, reviewer_id, period_start, period_end,
                                 overall_score, manager_comments, employee_comments, stage, status)
SELECT 'PRV-0003', e.id, r.id, '2026-01-01', '2026-06-30', NULL,
       'Strong first half; awaiting employee feedback.', NULL,
       'MANAGER_REVIEW', 'IN_PROGRESS'
FROM (SELECT id FROM employees WHERE employee_code = 'EMP-0005') e
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0002') r;

INSERT INTO performance_reviews (review_code, employee_id, reviewer_id, period_start, period_end,
                                 overall_score, manager_comments, employee_comments, stage, status)
SELECT 'PRV-0004', e.id, r.id, '2026-01-01', '2026-06-30', NULL,
       NULL, NULL, 'MANAGER_REVIEW', 'DRAFT'
FROM (SELECT id FROM employees WHERE employee_code = 'EMP-0002') e
JOIN (SELECT id FROM employees WHERE employee_code = 'EMP-0001') r;

-- Scored items (scores 1..5 per criterion)
INSERT INTO performance_review_items (performance_review_id, criteria_id, score, comments)
SELECT r.id, c.id, v.score, v.comments
FROM performance_reviews r
JOIN (SELECT 'PROD' AS code, 4.5 AS score, 'Excellent output volume.'      AS comments UNION ALL
      SELECT 'QUAL', 4.0, 'Clean, reliable deliverables.'                            UNION ALL
      SELECT 'COMM', 3.5, 'Clear in standups; improve written updates.'              UNION ALL
      SELECT 'TEAM', 4.0, 'Always willing to help teammates.'                        UNION ALL
      SELECT 'ATT',  5.0, 'Perfect attendance record.'                               UNION ALL
      SELECT 'LEAD', 3.0, 'Growing mentoring skills.'                                UNION ALL
      SELECT 'TECH', 4.5, 'Deep framework knowledge.') v
JOIN performance_criteria c ON c.criteria_code = v.code
WHERE r.review_code = 'PRV-0001';

INSERT INTO performance_review_items (performance_review_id, criteria_id, score, comments)
SELECT r.id, c.id, v.score, v.comments
FROM performance_reviews r
JOIN (SELECT 'PROD' AS code, 3.5 AS score, 'Meets most deadlines.'        AS comments UNION ALL
      SELECT 'QUAL', 3.0, 'Occasional rework needed.'                              UNION ALL
      SELECT 'COMM', 4.0, 'Communicates proactively.'                              UNION ALL
      SELECT 'TEAM', 4.5, 'Great collaborator across teams.'                       UNION ALL
      SELECT 'ATT',  4.0, 'Reliable attendance.'                                   UNION ALL
      SELECT 'LEAD', 2.5, 'Developing ownership of tasks.'                         UNION ALL
      SELECT 'TECH', 3.5, 'Comfortable with core stack.') v
JOIN performance_criteria c ON c.criteria_code = v.code
WHERE r.review_code = 'PRV-0002';

INSERT INTO performance_review_items (performance_review_id, criteria_id, score, comments)
SELECT r.id, c.id, v.score, v.comments
FROM performance_reviews r
JOIN (SELECT 'PROD' AS code, 4.0 AS score, 'Strong, steady delivery.'     AS comments UNION ALL
      SELECT 'QUAL', 4.5, 'Attention to detail.'                                   UNION ALL
      SELECT 'COMM', 4.0, 'Good with stakeholders.'                                UNION ALL
      SELECT 'TEAM', 4.0, 'Supportive team player.'                                UNION ALL
      SELECT 'ATT',  4.5, 'Very punctual.'                                         UNION ALL
      SELECT 'LEAD', 3.5, 'Taking initiative on small projects.'                   UNION ALL
      SELECT 'TECH', 4.0, 'Quick learner.') v
JOIN performance_criteria c ON c.criteria_code = v.code
WHERE r.review_code = 'PRV-0003';

-- Read-only diagnostics for the approved 17-table MySQL source schema.
-- Results contain aggregate counts and domain labels only, never row-level PII.
SET SESSION time_zone = '+00:00';
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

SELECT 'row_count' AS section_name, 'analytics_events' AS check_name, COUNT(*) AS result_count FROM analytics_events
UNION ALL SELECT 'row_count','applications',COUNT(*) FROM applications
UNION ALL SELECT 'row_count','assessments',COUNT(*) FROM assessments
UNION ALL SELECT 'row_count','audit_log',COUNT(*) FROM audit_log
UNION ALL SELECT 'row_count','candidate_scores',COUNT(*) FROM candidate_scores
UNION ALL SELECT 'row_count','chatbot_queries',COUNT(*) FROM chatbot_queries
UNION ALL SELECT 'row_count','criteria',COUNT(*) FROM criteria
UNION ALL SELECT 'row_count','cv_profiles',COUNT(*) FROM cv_profiles
UNION ALL SELECT 'row_count','departments',COUNT(*) FROM departments
UNION ALL SELECT 'row_count','job_templates',COUNT(*) FROM job_templates
UNION ALL SELECT 'row_count','jobs',COUNT(*) FROM jobs
UNION ALL SELECT 'row_count','notifications',COUNT(*) FROM notifications
UNION ALL SELECT 'row_count','permission_overrides',COUNT(*) FROM permission_overrides
UNION ALL SELECT 'row_count','sent_emails',COUNT(*) FROM sent_emails
UNION ALL SELECT 'row_count','settings',COUNT(*) FROM settings
UNION ALL SELECT 'row_count','staff',COUNT(*) FROM staff
UNION ALL SELECT 'row_count','users',COUNT(*) FROM users
ORDER BY check_name;

SELECT 'duplicate_ci' AS section_name, check_name, issue_count
FROM (
  SELECT 'users.email' check_name, COALESCE(SUM(n - 1),0) issue_count FROM
    (SELECT COUNT(*) n FROM users GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1) d
  UNION ALL SELECT 'staff.employee_number', COALESCE(SUM(n - 1),0) FROM
    (SELECT COUNT(*) n FROM staff GROUP BY LOWER(TRIM(employee_number)) HAVING COUNT(*) > 1) d
  UNION ALL SELECT 'departments.code', COALESCE(SUM(n - 1),0) FROM
    (SELECT COUNT(*) n FROM departments GROUP BY LOWER(TRIM(code)) HAVING COUNT(*) > 1) d
  UNION ALL SELECT 'applications.job_id+candidate_email', COALESCE(SUM(n - 1),0) FROM
    (SELECT COUNT(*) n FROM applications GROUP BY job_id, LOWER(TRIM(candidate_email)) HAVING COUNT(*) > 1) d
  UNION ALL SELECT 'cv_profiles.user_email', COALESCE(SUM(n - 1),0) FROM
    (SELECT COUNT(*) n FROM cv_profiles GROUP BY LOWER(TRIM(user_email)) HAVING COUNT(*) > 1) d
  UNION ALL SELECT 'permission_overrides.email', COALESCE(SUM(n - 1),0) FROM
    (SELECT COUNT(*) n FROM permission_overrides WHERE email IS NOT NULL
     GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1) d
) checks ORDER BY check_name;

SELECT 'orphan_reference' AS section_name, check_name, issue_count
FROM (
  SELECT 'analytics_events.job_id->jobs.id' check_name, COUNT(*) issue_count
    FROM analytics_events c LEFT JOIN jobs p ON p.id=c.job_id WHERE c.job_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'applications.job_id->jobs.id', COUNT(*) FROM applications c LEFT JOIN jobs p ON p.id=c.job_id WHERE p.id IS NULL
  UNION ALL SELECT 'applications.candidate_email->users.email(ci)', COUNT(*) FROM applications c LEFT JOIN users p ON LOWER(p.email)=LOWER(c.candidate_email) WHERE p.id IS NULL
  UNION ALL SELECT 'assessments.application_id->applications.id', COUNT(*) FROM assessments c LEFT JOIN applications p ON p.id=c.application_id WHERE p.id IS NULL
  UNION ALL SELECT 'assessments.scheduled_by->users.id', COUNT(*) FROM assessments c LEFT JOIN users p ON p.id=c.scheduled_by WHERE c.scheduled_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'assessments.recorded_by->users.id', COUNT(*) FROM assessments c LEFT JOIN users p ON p.id=c.recorded_by WHERE c.recorded_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'candidate_scores.application_id->applications.id', COUNT(*) FROM candidate_scores c LEFT JOIN applications p ON p.id=c.application_id WHERE p.id IS NULL
  UNION ALL SELECT 'candidate_scores.scorer_user_id->users.id', COUNT(*) FROM candidate_scores c LEFT JOIN users p ON p.id=c.scorer_user_id WHERE p.id IS NULL
  UNION ALL SELECT 'criteria.job_id->jobs.id', COUNT(*) FROM criteria c LEFT JOIN jobs p ON p.id=c.job_id WHERE p.id IS NULL
  UNION ALL SELECT 'cv_profiles.user_email->users.email(ci)', COUNT(*) FROM cv_profiles c LEFT JOIN users p ON LOWER(p.email)=LOWER(c.user_email) WHERE p.id IS NULL
  UNION ALL SELECT 'departments.head_user_id->users.id', COUNT(*) FROM departments c LEFT JOIN users p ON p.id=c.head_user_id WHERE c.head_user_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'job_templates.department_id->departments.id', COUNT(*) FROM job_templates c LEFT JOIN departments p ON p.id=c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'job_templates.source_job_id->jobs.id', COUNT(*) FROM job_templates c LEFT JOIN jobs p ON p.id=c.source_job_id WHERE c.source_job_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'job_templates.created_by->users.id', COUNT(*) FROM job_templates c LEFT JOIN users p ON p.id=c.created_by WHERE c.created_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'jobs.created_by->users.id', COUNT(*) FROM jobs c LEFT JOIN users p ON p.id=c.created_by WHERE c.created_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'jobs.department_id->departments.id', COUNT(*) FROM jobs c LEFT JOIN departments p ON p.id=c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'jobs.reviewed_by->users.id', COUNT(*) FROM jobs c LEFT JOIN users p ON p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'jobs.approved_by->users.id', COUNT(*) FROM jobs c LEFT JOIN users p ON p.id=c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'permission_overrides.admin_id->users.id', COUNT(*) FROM permission_overrides c LEFT JOIN users p ON p.id=c.admin_id WHERE c.admin_id IS NOT NULL AND p.id IS NULL
  UNION ALL SELECT 'permission_overrides.email->users.email(ci)', COUNT(*) FROM permission_overrides c LEFT JOIN users p ON LOWER(p.email)=LOWER(c.email) WHERE c.email IS NOT NULL AND p.id IS NULL
) checks ORDER BY check_name;

SELECT 'invalid_domain' AS section_name, check_name, issue_count
FROM (
  SELECT 'applications.completion outside 0..100' check_name, COUNT(*) issue_count FROM applications WHERE completion NOT BETWEEN 0 AND 100
  UNION ALL SELECT 'applications.cgpa outside 0..5', COUNT(*) FROM applications WHERE cgpa IS NOT NULL AND cgpa NOT BETWEEN 0 AND 5
  UNION ALL SELECT 'assessments.score outside 0..100', COUNT(*) FROM assessments WHERE score IS NOT NULL AND score NOT BETWEEN 0 AND 100
  UNION ALL SELECT 'assessments.passed not boolean', COUNT(*) FROM assessments WHERE passed IS NOT NULL AND passed NOT IN (0,1)
  UNION ALL SELECT 'candidate_scores.score outside 0..100', COUNT(*) FROM candidate_scores WHERE score NOT BETWEEN 0 AND 100
  UNION ALL SELECT 'criteria.min_cgpa outside 0..5', COUNT(*) FROM criteria WHERE min_cgpa IS NOT NULL AND min_cgpa NOT BETWEEN 0 AND 5
  UNION ALL SELECT 'criteria.min_experience_years negative', COUNT(*) FROM criteria WHERE min_experience_years < 0
  UNION ALL SELECT 'jobs.min_age outside 18..100', COUNT(*) FROM jobs WHERE min_age NOT BETWEEN 18 AND 100
  UNION ALL SELECT 'jobs.required_experience outside 0..80', COUNT(*) FROM jobs WHERE required_experience NOT BETWEEN 0 AND 80
  UNION ALL SELECT 'jobs.vacancies below 1', COUNT(*) FROM jobs WHERE vacancies < 1
  UNION ALL SELECT 'jobs.featured not boolean', COUNT(*) FROM jobs WHERE featured NOT IN (0,1)
  UNION ALL SELECT 'notifications.is_read not boolean', COUNT(*) FROM notifications WHERE is_read NOT IN (0,1)
  UNION ALL SELECT 'settings singleton count differs from 1', CASE WHEN COUNT(*)=1 THEN 0 ELSE 1 END FROM settings
  UNION ALL SELECT 'settings thresholds invalid', COUNT(*) FROM settings WHERE min_age_threshold NOT BETWEEN 18 AND 100
    OR session_timeout_minutes <= 0 OR closing_soon_days < 0 OR max_applications_per_candidate <= 0
    OR allow_external_internal_jobs NOT IN (0,1)
  UNION ALL SELECT 'users booleans invalid', COUNT(*) FROM users WHERE email_verified NOT IN (0,1) OR is_active NOT IN (0,1)
  UNION ALL SELECT 'users token_version negative', COUNT(*) FROM users WHERE token_version < 0
  UNION ALL SELECT 'users admin_role/account_type mismatch', COUNT(*) FROM users
    WHERE (account_type='admin' AND admin_role IS NULL) OR (account_type<>'admin' AND admin_role IS NOT NULL)
  UNION ALL SELECT 'analytics_events.event_type invalid', COUNT(*) FROM analytics_events WHERE event_type NOT IN ('page_view','job_view','apply_click','save_job','search')
  UNION ALL SELECT 'applications.status invalid', COUNT(*) FROM applications WHERE status NOT IN ('Pending','Under Review','Shortlisted','Interview','Assessment Scheduled','Assessment Complete','Shortlisted II','Background Check','Offered','Declined','Withdrawn')
  UNION ALL SELECT 'assessments.type invalid', COUNT(*) FROM assessments WHERE type NOT IN ('written','psychometric','interview','practical')
  UNION ALL SELECT 'chatbot_queries.outcome invalid', COUNT(*) FROM chatbot_queries WHERE outcome NOT IN ('answered','suggested','fallback')
  UNION ALL SELECT 'jobs enum invalid', COUNT(*) FROM jobs WHERE salary_band NOT IN ('UG1','UG2','UG3','UG4','UG5','UG6','UG7')
    OR type NOT IN ('Full-time','Contract','Fixed Term Contract') OR visibility NOT IN ('external','internal','closed')
    OR status NOT IN ('draft','pending_review','pending_approval','published','declined')
  UNION ALL SELECT 'notifications.type invalid', COUNT(*) FROM notifications WHERE type NOT IN ('shortlisted','declined','interview','offered','info')
  UNION ALL SELECT 'users enum invalid', COUNT(*) FROM users WHERE account_type NOT IN ('external','internal','admin')
    OR effective_type NOT IN ('external','internal','admin')
    OR (admin_role IS NOT NULL AND admin_role NOT IN ('super','hr','recruiter','auditor','hr_officer','it_admin','dhra','hod'))
  UNION ALL SELECT 'permission_overrides booleans invalid', COUNT(*) FROM permission_overrides WHERE
    can_view_applications NOT IN (0,1) OR can_shortlist NOT IN (0,1) OR can_screen_interns NOT IN (0,1)
    OR can_send_notifications NOT IN (0,1) OR can_manage_jobs NOT IN (0,1) OR can_manage_criteria NOT IN (0,1)
    OR can_view_staff NOT IN (0,1) OR can_export NOT IN (0,1) OR can_view_audit NOT IN (0,1)
    OR can_manage_settings NOT IN (0,1) OR can_grant_permissions NOT IN (0,1) OR can_review_job NOT IN (0,1)
    OR can_approve_job NOT IN (0,1) OR can_manage_departments NOT IN (0,1) OR can_manage_admins NOT IN (0,1)
    OR can_assign_rights NOT IN (0,1) OR can_schedule_assessment NOT IN (0,1) OR can_record_assessment NOT IN (0,1)
  UNION ALL SELECT 'blank required emails', COUNT(*) FROM users WHERE TRIM(email)=''
  UNION ALL SELECT 'blank application candidate emails', COUNT(*) FROM applications WHERE TRIM(candidate_email)=''
) checks ORDER BY check_name;

SELECT 'json_validity' AS section_name, check_name, issue_count
FROM (
  SELECT 'applications.screening_answers' check_name, COUNT(*) issue_count FROM applications WHERE screening_answers IS NOT NULL AND NOT JSON_VALID(screening_answers)
  UNION ALL SELECT 'criteria.required_keywords', COUNT(*) FROM criteria WHERE NOT JSON_VALID(required_keywords)
  UNION ALL SELECT 'criteria.screening_questions', COUNT(*) FROM criteria WHERE screening_questions IS NOT NULL AND NOT JSON_VALID(screening_questions)
  UNION ALL SELECT 'criteria.disqualifying_universities', COUNT(*) FROM criteria WHERE disqualifying_universities IS NOT NULL AND NOT JSON_VALID(disqualifying_universities)
  UNION ALL SELECT 'criteria.assessment_types', COUNT(*) FROM criteria WHERE assessment_types IS NOT NULL AND NOT JSON_VALID(assessment_types)
  UNION ALL SELECT 'criteria.requirements', COUNT(*) FROM criteria WHERE requirements IS NOT NULL AND NOT JSON_VALID(requirements)
  UNION ALL SELECT 'cv_profiles.personal_data', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(personal_data)
  UNION ALL SELECT 'cv_profiles.qualifications', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(qualifications)
  UNION ALL SELECT 'cv_profiles.skills', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(skills)
  UNION ALL SELECT 'cv_profiles.experience', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(experience)
  UNION ALL SELECT 'cv_profiles.referees', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(referees)
  UNION ALL SELECT 'cv_profiles.next_of_kin', COUNT(*) FROM cv_profiles WHERE NOT JSON_VALID(next_of_kin)
  UNION ALL SELECT 'job_templates.content', COUNT(*) FROM job_templates WHERE NOT JSON_VALID(content)
  UNION ALL SELECT 'jobs.accountabilities', COUNT(*) FROM jobs WHERE accountabilities IS NOT NULL AND NOT JSON_VALID(accountabilities)
  UNION ALL SELECT 'jobs.special_skills', COUNT(*) FROM jobs WHERE special_skills IS NOT NULL AND NOT JSON_VALID(special_skills)
) checks ORDER BY check_name;

SELECT 'enum_distribution' AS section_name, 'analytics_events.event_type' AS field_name, event_type AS domain_value, COUNT(*) AS result_count FROM analytics_events GROUP BY event_type
UNION ALL SELECT 'enum_distribution','applications.status',status,COUNT(*) FROM applications GROUP BY status
UNION ALL SELECT 'enum_distribution','assessments.type',type,COUNT(*) FROM assessments GROUP BY type
UNION ALL SELECT 'enum_distribution','chatbot_queries.outcome',outcome,COUNT(*) FROM chatbot_queries GROUP BY outcome
UNION ALL SELECT 'enum_distribution','jobs.salary_band',salary_band,COUNT(*) FROM jobs GROUP BY salary_band
UNION ALL SELECT 'enum_distribution','jobs.type',type,COUNT(*) FROM jobs GROUP BY type
UNION ALL SELECT 'enum_distribution','jobs.visibility',visibility,COUNT(*) FROM jobs GROUP BY visibility
UNION ALL SELECT 'enum_distribution','jobs.status',status,COUNT(*) FROM jobs GROUP BY status
UNION ALL SELECT 'enum_distribution','notifications.type',type,COUNT(*) FROM notifications GROUP BY type
UNION ALL SELECT 'enum_distribution','users.account_type',account_type,COUNT(*) FROM users GROUP BY account_type
UNION ALL SELECT 'enum_distribution','users.admin_role',COALESCE(admin_role,'<NULL>'),COUNT(*) FROM users GROUP BY admin_role
UNION ALL SELECT 'enum_distribution','users.effective_type',effective_type,COUNT(*) FROM users GROUP BY effective_type
ORDER BY field_name, domain_value;

COMMIT;

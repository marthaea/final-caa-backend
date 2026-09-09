\set ON_ERROR_STOP on

CREATE OR REPLACE FUNCTION migration_stage.valid_json(value text)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
  PERFORM value::jsonb;
  RETURN true;
EXCEPTION WHEN others THEN
  RETURN false;
END $$;

WITH counts(table_name, row_count) AS (
  SELECT 'analytics_events',count(*) FROM migration_stage.analytics_events UNION ALL
  SELECT 'applications',count(*) FROM migration_stage.applications UNION ALL
  SELECT 'assessments',count(*) FROM migration_stage.assessments UNION ALL
  SELECT 'audit_log',count(*) FROM migration_stage.audit_log UNION ALL
  SELECT 'candidate_scores',count(*) FROM migration_stage.candidate_scores UNION ALL
  SELECT 'chatbot_queries',count(*) FROM migration_stage.chatbot_queries UNION ALL
  SELECT 'criteria',count(*) FROM migration_stage.criteria UNION ALL
  SELECT 'cv_profiles',count(*) FROM migration_stage.cv_profiles UNION ALL
  SELECT 'departments',count(*) FROM migration_stage.departments UNION ALL
  SELECT 'job_templates',count(*) FROM migration_stage.job_templates UNION ALL
  SELECT 'jobs',count(*) FROM migration_stage.jobs UNION ALL
  SELECT 'notifications',count(*) FROM migration_stage.notifications UNION ALL
  SELECT 'permission_overrides',count(*) FROM migration_stage.permission_overrides UNION ALL
  SELECT 'sent_emails',count(*) FROM migration_stage.sent_emails UNION ALL
  SELECT 'settings',count(*) FROM migration_stage.settings UNION ALL
  SELECT 'staff',count(*) FROM migration_stage.staff UNION ALL
  SELECT 'users',count(*) FROM migration_stage.users
) SELECT 'row_count' AS section_name, * FROM counts ORDER BY table_name;

WITH ranges(table_name, min_id, max_id, invalid_ids) AS (
  SELECT 'analytics_events',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.analytics_events UNION ALL
  SELECT 'applications',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.applications UNION ALL
  SELECT 'assessments',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.assessments UNION ALL
  SELECT 'audit_log',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.audit_log UNION ALL
  SELECT 'candidate_scores',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.candidate_scores UNION ALL
  SELECT 'chatbot_queries',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.chatbot_queries UNION ALL
  SELECT 'criteria',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.criteria UNION ALL
  SELECT 'cv_profiles',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.cv_profiles UNION ALL
  SELECT 'departments',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.departments UNION ALL
  SELECT 'job_templates',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.job_templates UNION ALL
  SELECT 'jobs',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.jobs UNION ALL
  SELECT 'notifications',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.notifications UNION ALL
  SELECT 'permission_overrides',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.permission_overrides UNION ALL
  SELECT 'sent_emails',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.sent_emails UNION ALL
  SELECT 'settings',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.settings UNION ALL
  SELECT 'staff',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.staff UNION ALL
  SELECT 'users',min(id::bigint),max(id::bigint),count(*) FILTER (WHERE id !~ '^[1-9][0-9]*$') FROM migration_stage.users
) SELECT 'pk_range' AS section_name, * FROM ranges ORDER BY table_name;

WITH checks(check_name, issue_count) AS (
  SELECT 'settings singleton',(SELECT CASE WHEN count(*)=1 THEN 0 ELSE 1 END FROM migration_stage.settings)
  UNION ALL SELECT 'users.email duplicate(ci)',COALESCE(sum(n-1),0) FROM
    (SELECT count(*) n FROM migration_stage.users GROUP BY lower(btrim(email)) HAVING count(*)>1) d
  UNION ALL SELECT 'applications.job+email duplicate(ci)',COALESCE(sum(n-1),0) FROM
    (SELECT count(*) n FROM migration_stage.applications GROUP BY job_id,lower(btrim(candidate_email)) HAVING count(*)>1) d
  UNION ALL SELECT 'departments.code duplicate(ci)',COALESCE(sum(n-1),0) FROM
    (SELECT count(*) n FROM migration_stage.departments GROUP BY lower(btrim(code)) HAVING count(*)>1) d
  UNION ALL SELECT 'staff.employee_number duplicate(ci)',COALESCE(sum(n-1),0) FROM
    (SELECT count(*) n FROM migration_stage.staff GROUP BY lower(btrim(employee_number)) HAVING count(*)>1) d
  UNION ALL SELECT 'applications.job_id orphan',count(*) FROM migration_stage.applications c LEFT JOIN migration_stage.jobs p ON p.id=c.job_id WHERE p.id IS NULL
  UNION ALL SELECT 'analytics_events.job_id orphan',count(*) FROM migration_stage.analytics_events c LEFT JOIN migration_stage.jobs p ON p.id=c.job_id WHERE c.job_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'applications.candidate_email orphan(ci)',count(*) FROM migration_stage.applications c LEFT JOIN migration_stage.users p ON lower(p.email)=lower(c.candidate_email) WHERE p.id IS NULL
  UNION ALL SELECT 'assessments.application_id orphan',count(*) FROM migration_stage.assessments c LEFT JOIN migration_stage.applications p ON p.id=c.application_id WHERE p.id IS NULL
  UNION ALL SELECT 'assessments.scheduled_by orphan',count(*) FROM migration_stage.assessments c LEFT JOIN migration_stage.users p ON p.id=c.scheduled_by WHERE c.scheduled_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'assessments.recorded_by orphan',count(*) FROM migration_stage.assessments c LEFT JOIN migration_stage.users p ON p.id=c.recorded_by WHERE c.recorded_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'candidate_scores.application_id orphan',count(*) FROM migration_stage.candidate_scores c LEFT JOIN migration_stage.applications p ON p.id=c.application_id WHERE p.id IS NULL
  UNION ALL SELECT 'candidate_scores.scorer_user_id orphan',count(*) FROM migration_stage.candidate_scores c LEFT JOIN migration_stage.users p ON p.id=c.scorer_user_id WHERE p.id IS NULL
  UNION ALL SELECT 'criteria.job_id orphan',count(*) FROM migration_stage.criteria c LEFT JOIN migration_stage.jobs p ON p.id=c.job_id WHERE p.id IS NULL
  UNION ALL SELECT 'departments.head_user_id orphan',count(*) FROM migration_stage.departments c LEFT JOIN migration_stage.users p ON p.id=c.head_user_id WHERE c.head_user_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'jobs.created_by orphan',count(*) FROM migration_stage.jobs c LEFT JOIN migration_stage.users p ON p.id=c.created_by WHERE c.created_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'jobs.department_id orphan',count(*) FROM migration_stage.jobs c LEFT JOIN migration_stage.departments p ON p.id=c.department_id WHERE c.department_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'jobs.reviewed_by orphan',count(*) FROM migration_stage.jobs c LEFT JOIN migration_stage.users p ON p.id=c.reviewed_by WHERE c.reviewed_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'jobs.approved_by orphan',count(*) FROM migration_stage.jobs c LEFT JOIN migration_stage.users p ON p.id=c.approved_by WHERE c.approved_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'job_templates.department_id orphan',count(*) FROM migration_stage.job_templates c LEFT JOIN migration_stage.departments p ON p.id=c.department_id WHERE c.department_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'job_templates.source_job_id orphan',count(*) FROM migration_stage.job_templates c LEFT JOIN migration_stage.jobs p ON p.id=c.source_job_id WHERE c.source_job_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'job_templates.created_by orphan',count(*) FROM migration_stage.job_templates c LEFT JOIN migration_stage.users p ON p.id=c.created_by WHERE c.created_by<>'' AND p.id IS NULL
  UNION ALL SELECT 'permission_overrides.admin_id orphan',count(*) FROM migration_stage.permission_overrides c LEFT JOIN migration_stage.users p ON p.id=c.admin_id WHERE c.admin_id<>'' AND p.id IS NULL
  UNION ALL SELECT 'permission_overrides.email orphan(ci)',count(*) FROM migration_stage.permission_overrides c LEFT JOIN migration_stage.users p ON lower(p.email)=lower(c.email) WHERE c.email<>'' AND p.id IS NULL
  UNION ALL SELECT 'cv_profiles.user_email orphan(ci)',count(*) FROM migration_stage.cv_profiles c LEFT JOIN migration_stage.users p ON lower(p.email)=lower(c.user_email) WHERE p.id IS NULL
  UNION ALL SELECT 'applications.completion invalid',count(*) FROM migration_stage.applications WHERE completion !~ '^[0-9]+$' OR completion::int NOT BETWEEN 0 AND 100
  UNION ALL SELECT 'applications.cgpa invalid',count(*) FROM migration_stage.applications WHERE cgpa<>'' AND (cgpa !~ '^[0-9]+(\.[0-9]+)?$' OR cgpa::numeric NOT BETWEEN 0 AND 5)
  UNION ALL SELECT 'assessments.score invalid',count(*) FROM migration_stage.assessments WHERE score<>'' AND (score !~ '^[0-9]+(\.[0-9]+)?$' OR score::numeric NOT BETWEEN 0 AND 100)
  UNION ALL SELECT 'candidate_scores.score invalid',count(*) FROM migration_stage.candidate_scores WHERE score !~ '^[0-9]+(\.[0-9]+)?$' OR score::numeric NOT BETWEEN 0 AND 100
  UNION ALL SELECT 'boolean fields invalid',sum(n) FROM (
    SELECT count(*) n FROM migration_stage.users WHERE email_verified NOT IN ('0','1') OR is_active NOT IN ('0','1')
    UNION ALL SELECT count(*) FROM migration_stage.jobs WHERE featured NOT IN ('0','1')
    UNION ALL SELECT count(*) FROM migration_stage.notifications WHERE is_read NOT IN ('0','1')
    UNION ALL SELECT count(*) FROM migration_stage.assessments WHERE passed<>'' AND passed NOT IN ('0','1')
  ) b
) SELECT 'validation' AS section_name, * FROM checks ORDER BY check_name;

WITH json_checks(check_name, issue_count) AS (
  SELECT 'applications.screening_answers',count(*) FROM migration_stage.applications WHERE NOT migration_stage.source_is_null(__nulls,12) AND NOT migration_stage.valid_json(screening_answers)
  UNION ALL SELECT 'criteria.required_keywords',count(*) FROM migration_stage.criteria WHERE NOT migration_stage.valid_json(required_keywords)
  UNION ALL SELECT 'criteria.screening_questions',count(*) FROM migration_stage.criteria WHERE NOT migration_stage.source_is_null(__nulls,5) AND NOT migration_stage.valid_json(screening_questions)
  UNION ALL SELECT 'criteria.disqualifying_universities',count(*) FROM migration_stage.criteria WHERE NOT migration_stage.source_is_null(__nulls,8) AND NOT migration_stage.valid_json(disqualifying_universities)
  UNION ALL SELECT 'criteria.assessment_types',count(*) FROM migration_stage.criteria WHERE NOT migration_stage.source_is_null(__nulls,11) AND NOT migration_stage.valid_json(assessment_types)
  UNION ALL SELECT 'criteria.requirements',count(*) FROM migration_stage.criteria WHERE NOT migration_stage.source_is_null(__nulls,12) AND NOT migration_stage.valid_json(requirements)
  UNION ALL SELECT 'cv_profiles.personal_data',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(personal_data)
  UNION ALL SELECT 'cv_profiles.qualifications',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(qualifications)
  UNION ALL SELECT 'cv_profiles.skills',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(skills)
  UNION ALL SELECT 'cv_profiles.experience',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(experience)
  UNION ALL SELECT 'cv_profiles.referees',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(referees)
  UNION ALL SELECT 'cv_profiles.next_of_kin',count(*) FROM migration_stage.cv_profiles WHERE NOT migration_stage.valid_json(next_of_kin)
  UNION ALL SELECT 'job_templates.content',count(*) FROM migration_stage.job_templates WHERE NOT migration_stage.valid_json(content)
  UNION ALL SELECT 'jobs.accountabilities',count(*) FROM migration_stage.jobs WHERE NOT migration_stage.source_is_null(__nulls,29) AND NOT migration_stage.valid_json(accountabilities)
  UNION ALL SELECT 'jobs.special_skills',count(*) FROM migration_stage.jobs WHERE NOT migration_stage.source_is_null(__nulls,30) AND NOT migration_stage.valid_json(special_skills)
) SELECT 'json_validity' AS section_name,* FROM json_checks ORDER BY check_name;

SELECT 'domain_distribution' AS section_name,field_name,domain_value,row_count FROM (
  SELECT 'applications.status' field_name,status domain_value,count(*) row_count FROM migration_stage.applications GROUP BY status
  UNION ALL SELECT 'assessments.type',type,count(*) FROM migration_stage.assessments GROUP BY type
  UNION ALL SELECT 'jobs.salary_band',salary_band,count(*) FROM migration_stage.jobs GROUP BY salary_band
  UNION ALL SELECT 'jobs.type',type,count(*) FROM migration_stage.jobs GROUP BY type
  UNION ALL SELECT 'jobs.visibility',visibility,count(*) FROM migration_stage.jobs GROUP BY visibility
  UNION ALL SELECT 'jobs.status',status,count(*) FROM migration_stage.jobs GROUP BY status
  UNION ALL SELECT 'notifications.type',type,count(*) FROM migration_stage.notifications GROUP BY type
  UNION ALL SELECT 'users.account_type',account_type,count(*) FROM migration_stage.users GROUP BY account_type
  UNION ALL SELECT 'users.admin_role',domain_value,count(*) FROM (
    SELECT CASE WHEN migration_stage.source_is_null(__nulls,6) THEN '<NULL>' ELSE admin_role END domain_value
    FROM migration_stage.users
  ) roles GROUP BY domain_value
) d ORDER BY field_name,domain_value;

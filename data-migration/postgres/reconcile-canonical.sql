\set ON_ERROR_STOP on

-- PRECONDITION: approved canonical PostgreSQL DDL and reviewed
-- staging-to-canonical transformation have been applied. This file is read-only.
BEGIN;
SET TRANSACTION READ ONLY;

WITH names(table_name) AS (VALUES
 ('analytics_events'),('applications'),('assessments'),('audit_log'),
 ('candidate_scores'),('chatbot_queries'),('criteria'),('cv_profiles'),
 ('departments'),('job_templates'),('jobs'),('notifications'),
 ('permission_overrides'),('sent_emails'),('settings'),('staff'),('users')
), stage_counts AS (
 SELECT table_name,
   (xpath('/row/c/text()',query_to_xml(format('SELECT count(*) c FROM migration_stage.%I',table_name),false,true,'')))[1]::text::bigint row_count
 FROM names
), canonical_counts AS (
 SELECT table_name,
   (xpath('/row/c/text()',query_to_xml(format('SELECT count(*) c FROM public.%I',table_name),false,true,'')))[1]::text::bigint row_count
 FROM names
)
SELECT 'count_comparison' section_name,n.table_name,s.row_count source_rows,c.row_count canonical_rows,
       c.row_count-s.row_count difference
FROM names n JOIN stage_counts s USING(table_name) JOIN canonical_counts c USING(table_name)
ORDER BY n.table_name;

WITH names(table_name) AS (VALUES
 ('analytics_events'),('applications'),('assessments'),('audit_log'),
 ('candidate_scores'),('chatbot_queries'),('criteria'),('cv_profiles'),
 ('departments'),('job_templates'),('jobs'),('notifications'),
 ('permission_overrides'),('sent_emails'),('settings'),('staff'),('users')
)
SELECT 'pk_sequence' section_name,table_name,
       (xpath('/row/min/text()',query_to_xml(format('SELECT min(id) min FROM public.%I',table_name),false,true,'')))[1]::text::bigint min_id,
       (xpath('/row/max/text()',query_to_xml(format('SELECT max(id) max FROM public.%I',table_name),false,true,'')))[1]::text::bigint max_id,
       pg_get_serial_sequence(format('public.%I',table_name),'id') sequence_name,
       CASE WHEN pg_get_serial_sequence(format('public.%I',table_name),'id') IS NULL THEN NULL
            ELSE (xpath('/row/last/text()',query_to_xml(
              format('SELECT last_value last FROM %s',pg_get_serial_sequence(format('public.%I',table_name),'id')),
              false,true,'')))[1]::text::bigint END sequence_last_value
FROM names ORDER BY table_name;

WITH checks(check_name,issue_count) AS (
 SELECT 'analytics_events.job_id orphan',count(*) FROM public.analytics_events c LEFT JOIN public.jobs p ON p.id=c.job_id WHERE c.job_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'applications.job_id orphan',count(*) FROM public.applications c LEFT JOIN public.jobs p ON p.id=c.job_id WHERE p.id IS NULL
 UNION ALL SELECT 'applications.candidate_email orphan(ci)',count(*) FROM public.applications c LEFT JOIN public.users p ON lower(p.email)=lower(c.candidate_email) WHERE p.id IS NULL
 UNION ALL SELECT 'assessments.application_id orphan',count(*) FROM public.assessments c LEFT JOIN public.applications p ON p.id=c.application_id WHERE p.id IS NULL
 UNION ALL SELECT 'assessments.scheduled_by orphan',count(*) FROM public.assessments c LEFT JOIN public.users p ON p.id=c.scheduled_by WHERE c.scheduled_by IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'assessments.recorded_by orphan',count(*) FROM public.assessments c LEFT JOIN public.users p ON p.id=c.recorded_by WHERE c.recorded_by IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'candidate_scores.application_id orphan',count(*) FROM public.candidate_scores c LEFT JOIN public.applications p ON p.id=c.application_id WHERE p.id IS NULL
 UNION ALL SELECT 'candidate_scores.scorer_user_id orphan',count(*) FROM public.candidate_scores c LEFT JOIN public.users p ON p.id=c.scorer_user_id WHERE p.id IS NULL
 UNION ALL SELECT 'criteria.job_id orphan',count(*) FROM public.criteria c LEFT JOIN public.jobs p ON p.id=c.job_id WHERE p.id IS NULL
 UNION ALL SELECT 'departments.head_user_id orphan',count(*) FROM public.departments c LEFT JOIN public.users p ON p.id=c.head_user_id WHERE c.head_user_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'jobs.created_by orphan',count(*) FROM public.jobs c LEFT JOIN public.users p ON p.id=c.created_by WHERE c.created_by IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'jobs.department_id orphan',count(*) FROM public.jobs c LEFT JOIN public.departments p ON p.id=c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'jobs.reviewed_by orphan',count(*) FROM public.jobs c LEFT JOIN public.users p ON p.id=c.reviewed_by WHERE c.reviewed_by IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'jobs.approved_by orphan',count(*) FROM public.jobs c LEFT JOIN public.users p ON p.id=c.approved_by WHERE c.approved_by IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'job_templates.department_id orphan',count(*) FROM public.job_templates c LEFT JOIN public.departments p ON p.id=c.department_id WHERE c.department_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'job_templates.source_job_id orphan',count(*) FROM public.job_templates c LEFT JOIN public.jobs p ON p.id=c.source_job_id WHERE c.source_job_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'permission_overrides.admin_id orphan',count(*) FROM public.permission_overrides c LEFT JOIN public.users p ON p.id=c.admin_id WHERE c.admin_id IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'permission_overrides.email orphan(ci)',count(*) FROM public.permission_overrides c LEFT JOIN public.users p ON lower(p.email)=lower(c.email) WHERE c.email IS NOT NULL AND p.id IS NULL
 UNION ALL SELECT 'cv_profiles.user_email orphan(ci)',count(*) FROM public.cv_profiles c LEFT JOIN public.users p ON lower(p.email)=lower(c.user_email) WHERE p.id IS NULL
 UNION ALL SELECT 'settings singleton',CASE WHEN count(*)=1 THEN 0 ELSE 1 END FROM public.settings
 UNION ALL SELECT 'applications completion invalid',count(*) FROM public.applications WHERE completion NOT BETWEEN 0 AND 100
 UNION ALL SELECT 'application cgpa invalid',count(*) FROM public.applications WHERE cgpa IS NOT NULL AND cgpa NOT BETWEEN 0 AND 5
 UNION ALL SELECT 'assessment score invalid',count(*) FROM public.assessments WHERE score IS NOT NULL AND score NOT BETWEEN 0 AND 100
 UNION ALL SELECT 'candidate score invalid',count(*) FROM public.candidate_scores WHERE score NOT BETWEEN 0 AND 100
 UNION ALL SELECT 'job age/vacancy invalid',count(*) FROM public.jobs WHERE min_age NOT BETWEEN 18 AND 100 OR vacancies<1
 UNION ALL SELECT 'users email duplicate(ci)',COALESCE(sum(n-1),0) FROM
   (SELECT count(*) n FROM public.users GROUP BY lower(btrim(email)) HAVING count(*)>1) d
 UNION ALL SELECT 'applications job/email duplicate(ci)',COALESCE(sum(n-1),0) FROM
   (SELECT count(*) n FROM public.applications GROUP BY job_id,lower(btrim(candidate_email)) HAVING count(*)>1) d
)
SELECT 'canonical_validation' section_name,* FROM checks ORDER BY check_name;

SELECT 'domain_distribution' section_name,field_name,domain_value,row_count FROM (
 SELECT 'applications.status' field_name,status::text domain_value,count(*) row_count FROM public.applications GROUP BY status
 UNION ALL SELECT 'assessments.type',type::text,count(*) FROM public.assessments GROUP BY type
 UNION ALL SELECT 'jobs.salary_band',salary_band::text,count(*) FROM public.jobs GROUP BY salary_band
 UNION ALL SELECT 'jobs.type',type::text,count(*) FROM public.jobs GROUP BY type
 UNION ALL SELECT 'jobs.visibility',visibility::text,count(*) FROM public.jobs GROUP BY visibility
 UNION ALL SELECT 'jobs.status',status::text,count(*) FROM public.jobs GROUP BY status
 UNION ALL SELECT 'notifications.type',type::text,count(*) FROM public.notifications GROUP BY type
 UNION ALL SELECT 'users.account_type',account_type::text,count(*) FROM public.users GROUP BY account_type
 UNION ALL SELECT 'users.admin_role',COALESCE(admin_role::text,'<NULL>'),count(*) FROM public.users GROUP BY admin_role
 UNION ALL SELECT 'users.effective_type',effective_type::text,count(*) FROM public.users GROUP BY effective_type
) d ORDER BY field_name,domain_value;

ROLLBACK;

\set ON_ERROR_STOP on

DROP SCHEMA IF EXISTS migration_stage CASCADE;
CREATE SCHEMA migration_stage;
REVOKE ALL ON SCHEMA migration_stage FROM PUBLIC;

CREATE FUNCTION migration_stage.source_is_null(bitmap text, zero_based_index integer)
RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT zero_based_index::text = ANY (string_to_array(bitmap, ',')) $$;

CREATE FUNCTION migration_stage.raise_count_mismatch(table_name text, expected bigint, actual bigint)
RETURNS bigint LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Staged row count mismatch for %: expected %, got %', table_name, expected, actual;
END $$;

CREATE TABLE migration_stage.analytics_events (
  id text, event_type text, job_id text, job_title text, query text, session_id text, created_at text, __nulls text);
CREATE TABLE migration_stage.applications (
  id text, job_id text, candidate_email text, candidate_name text, abbr text, title text, dept text, date text,
  status text, completion text, cgpa text, university text, screening_answers text, applied_at text, updated_at text,
  deployment_station text, deployment_date text, __nulls text);
CREATE TABLE migration_stage.assessments (
  id text, application_id text, type text, scheduled_at text, venue text, scheduled_by text, score text, passed text,
  notes text, recorded_by text, created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.audit_log (
  id text, at text, actor text, role text, action text, target text, __nulls text);
CREATE TABLE migration_stage.candidate_scores (
  id text, application_id text, scorer_user_id text, score text, comment text, created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.chatbot_queries (
  id text, query text, matched_question text, outcome text, persona text, asked_at text, __nulls text);
CREATE TABLE migration_stage.criteria (
  id text, job_id text, min_cgpa text, required_keywords text, notes text, screening_questions text,
  min_experience_years text, required_qual_level text, disqualifying_universities text, created_at text,
  updated_at text, assessment_types text, requirements text, __nulls text);
CREATE TABLE migration_stage.cv_profiles (
  id text, user_email text, personal_data text, highest_level text, qualifications text, skills text, experience text,
  referees text, next_of_kin text, photo_url text, created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.departments (
  id text, name text, code text, head_user_id text, created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.job_templates (
  id text, name text, department_id text, source_job_id text, content text, created_by text, created_at text, __nulls text);
CREATE TABLE migration_stage.jobs (
  id text, abbr text, title text, dept text, dept_key text, location text, salary text, salary_band text, type text,
  closes text, closes_at text, visibility text, min_age text, required_experience text, required_qualification text,
  description text, featured text, created_by text, created_at text, updated_at text, status text, department_id text,
  reviewed_by text, approved_by text, decline_reason text, job_ref text, reports_to text, vacancies text, about_role text,
  accountabilities text, special_skills text, __nulls text);
CREATE TABLE migration_stage.notifications (
  id text, recipient_email text, title text, message text, is_read text, type text, created_at text, __nulls text);
CREATE TABLE migration_stage.permission_overrides (
  id text, admin_id text, can_view_applications text, can_shortlist text, can_screen_interns text,
  can_send_notifications text, can_manage_jobs text, can_manage_criteria text, can_view_staff text, can_export text,
  can_view_audit text, can_manage_settings text, can_grant_permissions text, created_at text, updated_at text,
  can_review_job text, can_approve_job text, can_manage_departments text, can_manage_admins text, can_assign_rights text,
  can_schedule_assessment text, can_record_assessment text, email text, role text, __nulls text);
CREATE TABLE migration_stage.sent_emails (
  id text, to_email text, candidate_name text, subject text, body text, trigger_event text, job_title text, sent_at text, __nulls text);
CREATE TABLE migration_stage.settings (
  id text, org_name text, email_sender_name text, min_age_threshold text, allow_external_internal_jobs text,
  session_timeout_minutes text, closing_soon_days text, max_applications_per_candidate text,
  notif_template_shortlist text, notif_template_decline text, notif_template_interview text, notif_template_offer text,
  created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.staff (
  id text, employee_number text, first_name text, last_name text, dept text, position text, email text, joined_date text,
  status text, created_at text, updated_at text, __nulls text);
CREATE TABLE migration_stage.users (
  id text, email text, password_hash text, first_name text, last_name text, account_type text, admin_role text,
  employee_number text, effective_type text, created_at text, updated_at text, email_verified text, verify_token text,
  is_active text, token_version text, reset_token_hash text, reset_token_expires text, __nulls text);

REVOKE ALL ON ALL TABLES IN SCHEMA migration_stage FROM PUBLIC;

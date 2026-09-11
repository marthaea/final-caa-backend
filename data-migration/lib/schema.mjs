export const TABLES = Object.freeze([
  ['analytics_events', ['id', 'event_type', 'job_id', 'job_title', 'query', 'session_id', 'created_at']],
  ['users', ['id', 'email', 'password_hash', 'first_name', 'last_name', 'account_type', 'admin_role', 'employee_number', 'effective_type', 'created_at', 'updated_at', 'email_verified', 'verify_token', 'is_active', 'token_version', 'reset_token_hash', 'reset_token_expires']],
  ['departments', ['id', 'name', 'code', 'head_user_id', 'created_at', 'updated_at']],
  ['jobs', ['id', 'abbr', 'title', 'dept', 'dept_key', 'location', 'salary', 'salary_band', 'type', 'closes', 'closes_at', 'visibility', 'min_age', 'required_experience', 'required_qualification', 'description', 'featured', 'created_by', 'created_at', 'updated_at', 'status', 'department_id', 'reviewed_by', 'approved_by', 'decline_reason', 'job_ref', 'reports_to', 'vacancies', 'about_role', 'accountabilities', 'special_skills']],
  ['applications', ['id', 'job_id', 'candidate_email', 'candidate_name', 'abbr', 'title', 'dept', 'date', 'status', 'completion', 'cgpa', 'university', 'screening_answers', 'applied_at', 'updated_at', 'deployment_station', 'deployment_date']],
  ['assessments', ['id', 'application_id', 'type', 'scheduled_at', 'venue', 'scheduled_by', 'score', 'passed', 'notes', 'recorded_by', 'created_at', 'updated_at']],
  ['audit_log', ['id', 'at', 'actor', 'role', 'action', 'target']],
  ['candidate_scores', ['id', 'application_id', 'scorer_user_id', 'score', 'comment', 'created_at', 'updated_at']],
  ['chatbot_queries', ['id', 'query', 'matched_question', 'outcome', 'persona', 'asked_at']],
  ['criteria', ['id', 'job_id', 'min_cgpa', 'required_keywords', 'notes', 'screening_questions', 'min_experience_years', 'required_qual_level', 'disqualifying_universities', 'created_at', 'updated_at', 'assessment_types', 'requirements']],
  ['cv_profiles', ['id', 'user_email', 'personal_data', 'highest_level', 'qualifications', 'skills', 'experience', 'referees', 'next_of_kin', 'photo_url', 'created_at', 'updated_at']],
  ['job_templates', ['id', 'name', 'department_id', 'source_job_id', 'content', 'created_by', 'created_at']],
  ['notifications', ['id', 'recipient_email', 'title', 'message', 'is_read', 'type', 'created_at']],
  ['permission_overrides', ['id', 'admin_id', 'can_view_applications', 'can_shortlist', 'can_screen_interns', 'can_send_notifications', 'can_manage_jobs', 'can_manage_criteria', 'can_view_staff', 'can_export', 'can_view_audit', 'can_manage_settings', 'can_grant_permissions', 'created_at', 'updated_at', 'can_review_job', 'can_approve_job', 'can_manage_departments', 'can_manage_admins', 'can_assign_rights', 'can_schedule_assessment', 'can_record_assessment', 'email', 'role']],
  ['sent_emails', ['id', 'to_email', 'candidate_name', 'subject', 'body', 'trigger_event', 'job_title', 'sent_at']],
  ['settings', ['id', 'org_name', 'email_sender_name', 'min_age_threshold', 'allow_external_internal_jobs', 'session_timeout_minutes', 'closing_soon_days', 'max_applications_per_candidate', 'notif_template_shortlist', 'notif_template_decline', 'notif_template_interview', 'notif_template_offer', 'created_at', 'updated_at']],
  ['staff', ['id', 'employee_number', 'first_name', 'last_name', 'dept', 'position', 'email', 'joined_date', 'status', 'created_at', 'updated_at']]
].map(([name, columns]) => Object.freeze({ name, columns: Object.freeze(columns) })));

export const TABLE_NAMES = Object.freeze(TABLES.map(({ name }) => name));

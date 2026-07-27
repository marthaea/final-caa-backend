# Database Schema

Database name: `railway` (Railway-hosted MySQL 8; the original local setup used `caa-recruit` in phpMyAdmin — same schema either way).

Run all CREATE TABLE statements against your database. Run `SET FOREIGN_KEY_CHECKS = 0;` first if you need to recreate tables in any order. In practice, schema changes ship through `scripts/migrate.js` (idempotent — safe to run repeatedly), not by hand-running these statements; they're documented here to describe the shape, not as the literal migration mechanism.

This file reflects the schema actually running in production (verified via `SHOW CREATE TABLE` against every table), not just what shipped in the original build — several tables and columns below were added across later phases (hierarchical roles/job-approval workflow, assessments, job templates/structured requirements/candidate scoring). `background_checks` and `can_manage_background_checks` existed for one release and were dropped — they're intentionally absent below.

---

## Table 1 — users

Stores all accounts: candidates (external/internal) and admins.

```sql
CREATE TABLE IF NOT EXISTS users (
  id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email            VARCHAR(255) NOT NULL UNIQUE,
  password_hash    VARCHAR(255) NOT NULL,
  first_name       VARCHAR(100) NOT NULL,
  last_name        VARCHAR(100) NOT NULL,
  account_type     ENUM('external','internal','admin') NOT NULL DEFAULT 'external',
  admin_role       ENUM('super','hr','recruiter','auditor','hr_officer','it_admin','dhra','hod') NULL,
  employee_number  VARCHAR(50)  NULL,
  effective_type   ENUM('external','internal','admin') NOT NULL DEFAULT 'external',
  email_verified   TINYINT(1)   NOT NULL DEFAULT 1,
  verify_token     VARCHAR(64)  NULL,
  is_active        TINYINT(1)   NOT NULL DEFAULT 1,
  token_version    INT UNSIGNED NOT NULL DEFAULT 0,
  reset_token_hash VARCHAR(64)  NULL,
  reset_token_expires DATETIME  NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| Column | Purpose |
|--------|---------|
| `account_type` | The real type of the account |
| `effective_type` | What the user "acts as" — can differ from account_type for special cases |
| `admin_role` | Only set when account_type = admin. `super`/`hr`/`recruiter` are the original three; `auditor`, `hr_officer`, `it_admin`, `dhra`, `hod` are CAA's hierarchical roles layered on top (see `permission_overrides` below and `config/constants.js` `ROLE_DEFAULTS`) |
| `email_verified` | 0 until the user clicks the verification link. Default 1 so accounts created before this feature are not nagged; registration explicitly inserts 0 |
| `verify_token` | Single-use 64-char hex token for the verification link; cleared on verification |
| `employee_number` | Only set when account_type = internal |
| `is_active` | 0 blocks login and token refresh (admin deactivation) |
| `token_version` | Embedded in refresh JWTs; bumping it invalidates all outstanding refresh tokens (logout, password reset) |
| `reset_token_hash` | SHA-256 of the single-use password reset token — only the hash is stored |
| `reset_token_expires` | Reset link validity cutoff (1 hour from request) |

---

## Table 2 — jobs

Job vacancies posted by HR admins. Grew substantially across the job-approval workflow and job-creation-overhaul phases — `status`/`department_id`/`reviewed_by`/`approved_by`/`decline_reason` back the HOD→DHRA approval pipeline; `job_ref` through `special_skills` back the single-page creation form with structured, candidate-facing content (replacing the old hardcoded per-job demo detail page).

```sql
CREATE TABLE IF NOT EXISTS jobs (
  id                      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  abbr                    VARCHAR(10)  NOT NULL,
  title                   VARCHAR(255) NOT NULL,
  dept                    VARCHAR(100) NOT NULL,
  dept_key                VARCHAR(50)  NOT NULL,
  location                VARCHAR(100) NOT NULL DEFAULT 'Entebbe, Uganda',
  salary                  VARCHAR(100) NOT NULL,           -- full range; admin-only, never sent to candidate-facing surfaces
  salary_band             ENUM('UG1','UG2','UG3','UG4','UG5','UG6','UG7') NOT NULL,  -- "Salary Scale" — the only pay info candidates see
  type                    ENUM('Full-time','Contract','Fixed Term Contract') NOT NULL DEFAULT 'Full-time',
  closes                  VARCHAR(50)  NOT NULL,
  closes_at               DATE         NOT NULL,
  visibility              ENUM('external','internal','closed') NOT NULL DEFAULT 'external',
  min_age                 INT UNSIGNED NOT NULL DEFAULT 21,
  required_experience     INT UNSIGNED NOT NULL DEFAULT 0,
  required_qualification  VARCHAR(50)  NOT NULL,
  description             TEXT         NULL,
  featured                TINYINT(1)   NOT NULL DEFAULT 0,
  created_by              INT UNSIGNED NULL,
  created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  status                  ENUM('draft','pending_review','pending_approval','published','declined') NOT NULL DEFAULT 'published',
  department_id           INT UNSIGNED NULL,
  reviewed_by             INT UNSIGNED NULL,               -- HOD who reviewed it
  approved_by             INT UNSIGNED NULL,                -- DHRA who approved it
  decline_reason          TEXT         NULL,
  job_ref                 VARCHAR(100) NULL,                -- e.g. "UCAA/ADV/EXT/01/2026"
  reports_to              VARCHAR(255) NULL,
  vacancies               INT UNSIGNED NOT NULL DEFAULT 1,
  about_role              TEXT         NULL,                -- "Job Purpose" — separate from `description` (which is admin-only notes)
  accountabilities        JSON         NULL,                -- [{ area, activities: [...] }] — candidate-facing
  special_skills          JSON         NULL,                -- string[] — candidate-facing
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 3 — applications

Candidate applications for jobs.

```sql
CREATE TABLE IF NOT EXISTS applications (
  id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  job_id           INT UNSIGNED NOT NULL,
  candidate_email  VARCHAR(255) NOT NULL,
  candidate_name   VARCHAR(255) NOT NULL,
  abbr             VARCHAR(10)  NOT NULL,
  title            VARCHAR(255) NOT NULL,
  dept             VARCHAR(100) NOT NULL,
  date             VARCHAR(50)  NOT NULL,
  status           ENUM('Pending','Under Review','Shortlisted','Shortlisted II','Interview',
                        'Assessment Scheduled','Assessment Complete','Offered','Declined','Withdrawn')
                   NOT NULL DEFAULT 'Pending',
  completion       INT          NOT NULL DEFAULT 0,
  cgpa             DECIMAL(3,2) NULL,
  university       VARCHAR(255) NULL,
  screening_answers JSON        NULL,
  applied_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deployment_station VARCHAR(255) NULL,
  deployment_date  DATE         NULL,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
  UNIQUE KEY uq_application (job_id, candidate_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`Shortlisted II` sits between `Shortlisted` and `Interview` in application order — it's the CV-scoring stage backed by `candidate_scores` below. `Background Check` existed briefly as a status between `Assessment Complete` and `Offered`; the feature was removed and no longer appears in the enum. `deployment_station`/`deployment_date` are set via `PUT /applications/:id/deployment` (gated on `canShortlist`) once a candidate is `Offered`.

---

## Table 4 — cv_profiles

One CV profile per user (upserted on save).

```sql
CREATE TABLE IF NOT EXISTS cv_profiles (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_email    VARCHAR(255) NOT NULL UNIQUE,
  personal_data JSON         NOT NULL DEFAULT ('{}'),
  highest_level VARCHAR(50)  NULL,
  qualifications JSON        NOT NULL DEFAULT ('[]'),
  skills        JSON         NOT NULL DEFAULT ('[]'),
  experience    JSON         NOT NULL DEFAULT ('[]'),
  referees      JSON         NOT NULL DEFAULT ('[]'),
  next_of_kin   JSON         NOT NULL DEFAULT ('{}'),
  photo_url     TEXT         NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

A row only exists for a candidate who has actually saved a CV through the portal's Apply/CV flow — most of the bulk demo/seed application data was inserted directly into `applications` and never went through that flow, so it has no matching row here. Admin CV downloads (single or batch ZIP) only ever include candidates who do.

---

## Table 5 — criteria

Screening criteria per job — now also the home of the structured, qualifier/disqualifier requirement builder from the job-creation overhaul.

```sql
CREATE TABLE IF NOT EXISTS criteria (
  id                        INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  job_id                    INT UNSIGNED NOT NULL UNIQUE,
  min_cgpa                  DECIMAL(3,2) NULL,
  required_keywords         JSON         NOT NULL DEFAULT ('[]'),
  notes                     TEXT         NULL,
  screening_questions       JSON         NULL,
  min_experience_years      INT          NULL,
  required_qual_level       VARCHAR(50)  NULL,
  disqualifying_universities JSON        NULL,
  assessment_types          JSON         NULL,               -- which of written/psychometric/interview/practical apply
  requirements              JSON         NULL,                -- JobRequirement[] — the structured qualifier/disqualifier builder
  created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`requirements` holds the structured builder's output: each entry has a `kind` (minAge/maxAge/flyingHours/experienceYears/sex/qualificationLevel/specificDegree/oLevelSubject/aLevelSubject/custom), a `usage` (qualifier/disqualifier/criteriaOnly), and `mandatory` (essential vs. desirable). Saving one auto-generates the matching `screening_questions` entry (for qualifier/disqualifier usage) and/or sets the relevant scalar field on `jobs`/`criteria` — it doesn't replace `required_keywords`/`disqualifying_universities`/manual `screening_questions`, which remain available as a fallback. `GET /api/criteria/:jobId/public` (no auth) strips anything with `usage: 'criteriaOnly'` before returning — that's what candidates actually see on `/apply` and `/job`.

---

## Table 6 — settings

Single-row portal configuration table.

```sql
CREATE TABLE IF NOT EXISTS settings (
  id                           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  org_name                     VARCHAR(255) NOT NULL DEFAULT 'Uganda Civil Aviation Authority',
  email_sender_name            VARCHAR(255) NOT NULL DEFAULT 'CAA HR Team',
  min_age_threshold            INT          NOT NULL DEFAULT 21,
  allow_external_internal_jobs TINYINT(1)   NOT NULL DEFAULT 0,
  session_timeout_minutes      INT          NOT NULL DEFAULT 30,
  closing_soon_days            INT          NOT NULL DEFAULT 7,
  max_applications_per_candidate INT        NOT NULL DEFAULT 5,
  notif_template_shortlist     TEXT         NULL,
  notif_template_decline       TEXT         NULL,
  notif_template_interview     TEXT         NULL,
  notif_template_offer         TEXT         NULL,
  created_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`GET /api/settings` is intentionally public (no auth) — an anonymous visitor's very first page load needs it, and gating it previously caused every anonymous visit to get silently redirected to `/login`. The row must exist (seeded once); an empty table causes 500s wherever a route reads it. The four `notif_template_*` columns are the flattened form of what the frontend still calls `notifTemplates` (a single JSON object) — mapped at the route layer, not stored as JSON.

---

## Table 7 — permission_overrides

Per-admin permission overrides. Rows that don't exist fall back to `ROLE_DEFAULTS` in `config/constants.js`.

```sql
CREATE TABLE IF NOT EXISTS permission_overrides (
  id                     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  admin_id               INT UNSIGNED NULL UNIQUE,
  email                  VARCHAR(255) NULL UNIQUE,
  role                   VARCHAR(50)  NULL,
  can_view_applications  TINYINT(1)   NOT NULL DEFAULT 1,
  can_shortlist          TINYINT(1)   NOT NULL DEFAULT 0,
  can_screen_interns     TINYINT(1)   NOT NULL DEFAULT 0,
  can_send_notifications TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_jobs        TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_criteria    TINYINT(1)   NOT NULL DEFAULT 0,
  can_view_staff         TINYINT(1)   NOT NULL DEFAULT 0,
  can_export             TINYINT(1)   NOT NULL DEFAULT 0,
  can_view_audit         TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_settings    TINYINT(1)   NOT NULL DEFAULT 0,
  can_grant_permissions  TINYINT(1)   NOT NULL DEFAULT 0,
  can_review_job         TINYINT(1)   NOT NULL DEFAULT 0,
  can_approve_job        TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_departments TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_admins      TINYINT(1)   NOT NULL DEFAULT 0,
  can_assign_rights      TINYINT(1)   NOT NULL DEFAULT 0,
  can_schedule_assessment TINYINT(1)  NOT NULL DEFAULT 0,
  can_record_assessment  TINYINT(1)   NOT NULL DEFAULT 0,
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`can_review_job` through `can_assign_rights` back the hierarchical-roles/job-approval workflow (HOD reviews, DHRA approves, IT admin assigns rights). `can_schedule_assessment`/`can_record_assessment` were added with assessment scheduling but — until a later fix — were never actually wired into this route's read/write path, so no admin could have them granted or revoked through the UI; both now round-trip correctly. `can_manage_background_checks` existed for one release and has been fully removed, including from every `ROLE_DEFAULTS` entry.

---

## Table 8 — notifications

In-app notifications per recipient.

```sql
CREATE TABLE IF NOT EXISTS notifications (
  id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  recipient_email  VARCHAR(255) NOT NULL,
  title            VARCHAR(255) NOT NULL,
  message          TEXT         NOT NULL,
  is_read          TINYINT(1)   NOT NULL DEFAULT 0,
  type             ENUM('shortlisted','declined','interview','offered','info')
                   NOT NULL DEFAULT 'info',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 9 — sent_emails

Log of all emails sent through the system (HR use).

```sql
CREATE TABLE IF NOT EXISTS sent_emails (
  id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  to_email       VARCHAR(255) NOT NULL,
  candidate_name VARCHAR(255) NOT NULL,
  subject        VARCHAR(500) NOT NULL,
  body           TEXT         NOT NULL,
  trigger_event  VARCHAR(100) NOT NULL,
  job_title      VARCHAR(255) NOT NULL,
  sent_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 10 — audit_log

Immutable log of all admin actions.

```sql
CREATE TABLE IF NOT EXISTS audit_log (
  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actor      VARCHAR(255) NOT NULL,
  role       VARCHAR(100) NOT NULL,
  action     VARCHAR(255) NOT NULL,
  target     VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 11 — analytics_events

Tracks page views, job views, applications, searches (auto-purged after 90 days by cron).

```sql
CREATE TABLE IF NOT EXISTS analytics_events (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  event_type  ENUM('page_view','job_view','apply_click','save_job','search') NOT NULL,
  job_id      INT UNSIGNED NULL,
  job_title   VARCHAR(255) NULL,
  query       VARCHAR(500) NULL,
  session_id  VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 12 — staff

CAA employee registry. Internal users must have a matching row here.

```sql
CREATE TABLE IF NOT EXISTS staff (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  employee_number VARCHAR(50)  NOT NULL UNIQUE,
  first_name      VARCHAR(100) NOT NULL,
  last_name       VARCHAR(100) NOT NULL,
  dept            VARCHAR(100) NULL,
  position        VARCHAR(255) NULL,
  email           VARCHAR(255) NULL,
  joined_date     DATE         NULL,
  status          VARCHAR(50)  NOT NULL DEFAULT 'Active',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 13 — chatbot_queries

Questions typed to Martha (the frontend chatbot). Used by the "Martha" panel in
the HR Console's Site Analytics tab to surface questions she could not answer —
frequent entries are candidates for new FAQ content. Chip clicks and small talk
are not logged.

```sql
CREATE TABLE IF NOT EXISTS chatbot_queries (
  id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  query            VARCHAR(500) NOT NULL,
  matched_question VARCHAR(255) NULL,
  outcome          ENUM('answered','suggested','fallback') NOT NULL,
  persona          VARCHAR(20)  NOT NULL DEFAULT 'guest',
  asked_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_outcome_date (outcome, asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| Column | Purpose |
|--------|---------|
| `matched_question` | The FAQ question Martha matched (or her best "did you mean?" candidate) |
| `outcome` | `answered` = confident answer; `suggested` = weak match, offered candidates; `fallback` = no answer |
| `persona` | Who was chatting: guest, external, internal, recruiter, hr, or super |

---

## Table 14 — departments

CAA's organisational departments, each with a head user (the HOD in the job-approval workflow).

```sql
CREATE TABLE IF NOT EXISTS departments (
  id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  code         VARCHAR(20)  NOT NULL UNIQUE,
  head_user_id INT UNSIGNED NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (head_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`jobs.department_id` references this table; a job's HOD-review step routes to whoever is `head_user_id` for that department.

---

## Table 15 — assessments

Scheduling and scoring for the (up to 4) assessment types a job can require.

```sql
CREATE TABLE IF NOT EXISTS assessments (
  id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  application_id INT UNSIGNED NOT NULL,
  type           ENUM('written','psychometric','interview','practical') NOT NULL,
  scheduled_at   DATETIME     NULL,
  venue          VARCHAR(255) NULL,
  scheduled_by   INT UNSIGNED NULL,
  score          DECIMAL(5,2) NULL,
  passed         TINYINT(1)   NULL,
  notes          TEXT         NULL,
  recorded_by    INT UNSIGNED NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_app_type (application_id, type),
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`UNIQUE(application_id, type)` means exactly one row per assessment type per candidate — the last person to record a result wins. That's fine here since one candidate only has one written/psychometric/interview/practical outcome, but it's **not** suitable for multi-reviewer scoring — see `candidate_scores` below, which exists precisely because this table can't support more than one scorer.

---

## Table 16 — job_templates

Reusable job-content snapshots for the "start from a template" step in job creation.

```sql
CREATE TABLE IF NOT EXISTS job_templates (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(150) NOT NULL,
  department_id INT UNSIGNED NULL,
  source_job_id INT UNSIGNED NULL,
  content       JSON         NOT NULL,   -- snapshot of aboutRole, accountabilities, requirements, etc.
  created_by    INT UNSIGNED NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
  FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Seeded with 3 starter templates (technical/engineering, professional/administrative, graduate-entry/intern) via `scripts/seed-job-templates.js` — non-destructive, safe to re-run. HR can also save any in-progress draft as a new template ("Save as template" on the job creation page).

---

## Table 17 — candidate_scores

Multi-admin panel scoring at the `Shortlisted II` stage — one row per (application, scorer) pair, so simultaneous reviewers never overwrite each other.

```sql
CREATE TABLE IF NOT EXISTS candidate_scores (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  application_id  INT UNSIGNED NOT NULL,
  scorer_user_id  INT UNSIGNED NOT NULL,
  score           DECIMAL(5,2) NOT NULL,   -- 0–100
  comment         TEXT         NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_app_scorer (application_id, scorer_user_id),
  FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
  FOREIGN KEY (scorer_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`GET /api/candidate-scores?jobId=&status=` returns, per application, every scorer's row plus a computed average — every panelist sees everyone else's score and comment (never the candidate). "Auto-shortlist by score" reads that average against an admin-chosen threshold to bulk-advance/decline. CSV export/import round-trips scores for offline marking.

---

## Recommended Indexes (add after initial import for performance)

```sql
ALTER TABLE applications ADD INDEX idx_applications_email   (candidate_email);
ALTER TABLE applications ADD INDEX idx_applications_job     (job_id);
ALTER TABLE applications ADD INDEX idx_applications_status  (status);
ALTER TABLE notifications ADD INDEX idx_notif_email         (recipient_email);
ALTER TABLE audit_log    ADD INDEX idx_audit_at             (at);
ALTER TABLE analytics_events ADD INDEX idx_analytics_type   (event_type);
ALTER TABLE analytics_events ADD INDEX idx_analytics_at     (created_at);
```

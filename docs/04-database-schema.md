# Database Schema

Database name: `caa-recruit` (note the hyphen — use backticks in SQL: `` `caa-recruit` ``)

Run all CREATE TABLE statements in phpMyAdmin's SQL tab against the `caa-recruit` database. Run `SET FOREIGN_KEY_CHECKS = 0;` first if you need to recreate tables in any order.

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
  admin_role       ENUM('super','hr','recruiter') NULL,
  employee_number  VARCHAR(50)  NULL,
  effective_type   ENUM('external','internal','admin') NOT NULL DEFAULT 'external',
  email_verified   TINYINT(1)   NOT NULL DEFAULT 1,
  verify_token     VARCHAR(64)  NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| Column | Purpose |
|--------|---------|
| `account_type` | The real type of the account |
| `effective_type` | What the user "acts as" — can differ from account_type for special cases |
| `email_verified` | 0 until the user clicks the verification link. Default 1 so accounts created before this feature are not nagged; registration explicitly inserts 0 |
| `verify_token` | Single-use 64-char hex token for the verification link; cleared on verification |
| `admin_role` | Only set when account_type = admin |
| `employee_number` | Only set when account_type = internal |

---

## Table 2 — jobs

Job vacancies posted by HR admins.

```sql
CREATE TABLE IF NOT EXISTS jobs (
  id                      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  abbr                    VARCHAR(10)  NOT NULL,
  title                   VARCHAR(255) NOT NULL,
  dept                    VARCHAR(100) NOT NULL,
  dept_key                VARCHAR(50)  NOT NULL,
  location                VARCHAR(100) NOT NULL DEFAULT 'Entebbe, Uganda',
  salary                  VARCHAR(100) NOT NULL,
  salary_band             ENUM('UG1','UG2','UG3','UG4','UG5','UG6','UG7') NOT NULL,
  type                    ENUM('Full-time','Contract') NOT NULL DEFAULT 'Full-time',
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
  status           ENUM('Pending','Shortlisted','Interview','Offered','Declined','Withdrawn')
                   NOT NULL DEFAULT 'Pending',
  completion       INT          NOT NULL DEFAULT 0,
  cgpa             DECIMAL(3,2) NULL,
  university       VARCHAR(255) NULL,
  screening_answers JSON        NULL,
  applied_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
  UNIQUE KEY uq_application (job_id, candidate_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

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

---

## Table 5 — criteria

Screening criteria per job (min CGPA, screening questions, disqualifying universities).

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
  created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 6 — settings

Single-row portal configuration table.

```sql
CREATE TABLE IF NOT EXISTS settings (
  id                           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  min_age_threshold            INT          NOT NULL DEFAULT 21,
  allow_external_internal_jobs TINYINT(1)   NOT NULL DEFAULT 0,
  org_name                     VARCHAR(255) NOT NULL DEFAULT 'Uganda Civil Aviation Authority',
  session_timeout_minutes      INT          NOT NULL DEFAULT 30,
  email_sender_name            VARCHAR(255) NOT NULL DEFAULT 'CAA Recruitment',
  closing_soon_days            INT          NOT NULL DEFAULT 7,
  max_applications_per_candidate INT        NOT NULL DEFAULT 5,
  notif_templates              JSON         NULL,
  created_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Table 7 — permission_overrides

Per-admin permission overrides. Rows that don't exist fall back to ROLE_DEFAULTS.

```sql
CREATE TABLE IF NOT EXISTS permission_overrides (
  id                   INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  admin_id             INT UNSIGNED NOT NULL UNIQUE,
  can_view_applications TINYINT(1)  NOT NULL DEFAULT 1,
  can_shortlist        TINYINT(1)   NOT NULL DEFAULT 0,
  can_screen_interns   TINYINT(1)   NOT NULL DEFAULT 0,
  can_send_notifications TINYINT(1) NOT NULL DEFAULT 0,
  can_manage_jobs      TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_criteria  TINYINT(1)   NOT NULL DEFAULT 0,
  can_view_staff       TINYINT(1)   NOT NULL DEFAULT 0,
  can_export           TINYINT(1)   NOT NULL DEFAULT 0,
  can_view_audit       TINYINT(1)   NOT NULL DEFAULT 0,
  can_manage_settings  TINYINT(1)   NOT NULL DEFAULT 0,
  can_grant_permissions TINYINT(1)  NOT NULL DEFAULT 0,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

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
  department      VARCHAR(100) NULL,
  job_title       VARCHAR(255) NULL,
  email           VARCHAR(255) NULL,
  is_active       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

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

---

## Table — chatbot_queries

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

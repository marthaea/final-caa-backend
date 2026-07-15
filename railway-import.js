// railway-import.js
// Imports the full schema + seed data into Railway MySQL.
//
// Usage:
//   node railway-import.js <host> <port> <password>
//
// Example:
//   node railway-import.js roundhouse.proxy.rlwy.net 12345 lGeGSYoHWwWBIGNpmSNEtSXAnSytjYej
//
// The host and port come from Railway → MySQL service → Connect tab → Public URL

const mysql = require('mysql2/promise');
const fs    = require('fs');
const path  = require('path');

const [,, HOST, PORT, PASSWORD] = process.argv;

if (!HOST || !PORT || !PASSWORD) {
  console.error('Usage: node railway-import.js <host> <port> <password>');
  console.error('Example: node railway-import.js roundhouse.proxy.rlwy.net 12345 yourpassword');
  process.exit(1);
}

const SCHEMA = `
SET FOREIGN_KEY_CHECKS = 0;

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
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS applications (
  id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  job_id           INT UNSIGNED NOT NULL,
  candidate_email  VARCHAR(255) NOT NULL,
  candidate_name   VARCHAR(255) NOT NULL,
  abbr             VARCHAR(10)  NOT NULL,
  title            VARCHAR(255) NOT NULL,
  dept             VARCHAR(100) NOT NULL,
  date             VARCHAR(50)  NOT NULL,
  status           ENUM('Pending','Under Review','Shortlisted','Interview','Offered','Declined','Withdrawn') NOT NULL DEFAULT 'Pending',
  completion       INT          NOT NULL DEFAULT 0,
  cgpa             DECIMAL(3,2) NULL,
  university       VARCHAR(255) NULL,
  screening_answers JSON        NULL,
  applied_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE,
  UNIQUE KEY uq_application (job_id, candidate_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cv_profiles (
  id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_email     VARCHAR(255) NOT NULL UNIQUE,
  personal_data  JSON         NOT NULL DEFAULT ('{}'),
  highest_level  VARCHAR(50)  NULL,
  qualifications JSON         NOT NULL DEFAULT ('[]'),
  skills         JSON         NOT NULL DEFAULT ('[]'),
  experience     JSON         NOT NULL DEFAULT ('[]'),
  referees       JSON         NOT NULL DEFAULT ('[]'),
  next_of_kin    JSON         NOT NULL DEFAULT ('{}'),
  photo_url      TEXT         NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS criteria (
  id                         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  job_id                     INT UNSIGNED NOT NULL UNIQUE,
  min_cgpa                   DECIMAL(3,2) NULL,
  required_keywords          JSON         NOT NULL DEFAULT ('[]'),
  notes                      TEXT         NULL,
  screening_questions        JSON         NULL,
  min_experience_years       INT          NULL,
  required_qual_level        VARCHAR(50)  NULL,
  disqualifying_universities JSON         NULL,
  created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settings (
  id                             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  org_name                       VARCHAR(255) NOT NULL DEFAULT 'Uganda Civil Aviation Authority',
  email_sender_name              VARCHAR(255) NOT NULL DEFAULT 'CAA HR Team',
  min_age_threshold              INT          NOT NULL DEFAULT 21,
  allow_external_internal_jobs   TINYINT(1)   NOT NULL DEFAULT 0,
  session_timeout_minutes        INT          NOT NULL DEFAULT 30,
  closing_soon_days              INT          NOT NULL DEFAULT 7,
  max_applications_per_candidate INT          NOT NULL DEFAULT 5,
  notif_template_shortlist       TEXT         NULL,
  notif_template_decline         TEXT         NULL,
  notif_template_interview       TEXT         NULL,
  notif_template_offer           TEXT         NULL,
  created_at                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS permission_overrides (
  id                     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  admin_id               INT UNSIGNED NOT NULL UNIQUE,
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
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  recipient_email VARCHAR(255) NOT NULL,
  title           VARCHAR(255) NOT NULL,
  message         TEXT         NOT NULL,
  is_read         TINYINT(1)   NOT NULL DEFAULT 0,
  type            ENUM('shortlisted','declined','interview','offered','info') NOT NULL DEFAULT 'info',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

CREATE TABLE IF NOT EXISTS audit_log (
  id     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actor  VARCHAR(255) NOT NULL,
  role   VARCHAR(100) NOT NULL,
  action VARCHAR(255) NOT NULL,
  target VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analytics_events (
  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  event_type ENUM('page_view','job_view','apply_click','save_job','search') NOT NULL,
  job_id     INT UNSIGNED NULL,
  job_title  VARCHAR(255) NULL,
  query      VARCHAR(500) NULL,
  session_id VARCHAR(255) NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

ALTER TABLE applications     ADD INDEX IF NOT EXISTS idx_app_email  (candidate_email);
ALTER TABLE applications     ADD INDEX IF NOT EXISTS idx_app_job    (job_id);
ALTER TABLE applications     ADD INDEX IF NOT EXISTS idx_app_status (status);
ALTER TABLE notifications    ADD INDEX IF NOT EXISTS idx_notif_email(recipient_email);
ALTER TABLE audit_log        ADD INDEX IF NOT EXISTS idx_audit_at   (at);
ALTER TABLE analytics_events ADD INDEX IF NOT EXISTS idx_ana_type   (event_type);
ALTER TABLE analytics_events ADD INDEX IF NOT EXISTS idx_ana_at     (created_at);

SET FOREIGN_KEY_CHECKS = 1;
`;

async function splitStatements(sql) {
  return sql
    .split(/;\s*\n/)
    .map(s => s.trim())
    .filter(s => s.length > 0 && !s.startsWith('--'));
}

async function main() {
  const conn = await mysql.createConnection({
    host:     HOST,
    port:     parseInt(PORT),
    user:     'root',
    password: PASSWORD,
    database: 'railway',
    multipleStatements: false,
    ssl: { rejectUnauthorized: false },
  });

  console.log(`Connected to Railway MySQL at ${HOST}:${PORT}`);

  // 1 — Create tables
  console.log('\nCreating tables...');
  const schemaStmts = await splitStatements(SCHEMA);
  for (const stmt of schemaStmts) {
    try {
      await conn.execute(stmt);
    } catch (e) {
      if (!e.message.includes('Duplicate key name') && !e.message.includes('already exists')) {
        console.warn('  Warning:', e.message.slice(0, 80));
      }
    }
  }
  console.log('  Tables ready.');

  // 2 — Import seed data
  const seedFile = path.join(__dirname, 'caa-seed.sql');
  if (!fs.existsSync(seedFile)) {
    console.error('\ncaa-seed.sql not found. Run: node generate-sql.js');
    process.exit(1);
  }

  console.log('\nImporting seed data (caa-seed.sql)...');
  const seedSql = fs.readFileSync(seedFile, 'utf8');
  const seedStmts = await splitStatements(seedSql);

  let done = 0;
  for (const stmt of seedStmts) {
    if (stmt.startsWith('USE ') || stmt.startsWith('SET ')) {
      try { await conn.execute(stmt); } catch (_) {}
      continue;
    }
    try {
      await conn.execute(stmt);
      done++;
      if (done % 100 === 0) process.stdout.write(`\r  ${done} statements executed...`);
    } catch (e) {
      if (e.message.includes('Duplicate entry')) continue; // skip dupes gracefully
      console.warn(`\n  Warning on stmt ${done}: ${e.message.slice(0, 100)}`);
    }
  }

  console.log(`\n  ${done} statements executed successfully.`);
  console.log('\nDone! Your Railway database is fully seeded.');
  console.log('\nDemo credentials:');
  console.log('  Super Admin:  admin@caa.go.ug        / Admin@2026');
  console.log('  HR Director:  hrdirector@caa.go.ug   / HrDir@2026');
  console.log('  Recruiter:    recruit@caa.go.ug       / Recruit@2026');
  console.log('  Candidate:    jbukenya@gmail.com      / Demo@2026');

  await conn.end();
}

main().catch(err => {
  console.error('\nFailed:', err.message);
  process.exit(1);
});

// scripts/migrate.js
// Idempotent schema migration runner: checks information_schema for each
// column and only ALTERs what is missing, so it is safe to run repeatedly
// (npm run migrate) against dev or production.
require('dotenv').config();
const pool = require('../config/db');

const COLUMNS = [
  { table: 'users', name: 'is_active',           ddl: 'TINYINT(1) NOT NULL DEFAULT 1' },
  { table: 'users', name: 'token_version',       ddl: 'INT UNSIGNED NOT NULL DEFAULT 0' },
  { table: 'users', name: 'reset_token_hash',    ddl: 'VARCHAR(64) NULL' },
  { table: 'users', name: 'reset_token_expires', ddl: 'DATETIME NULL' },
  { table: 'criteria', name: 'assessment_types', ddl: "JSON NULL" },
  // Phase 2a — job-approval workflow
  { table: 'jobs', name: 'status',          ddl: "ENUM('draft','pending_review','pending_approval','published','declined') NOT NULL DEFAULT 'published'" },
  { table: 'jobs', name: 'department_id',   ddl: 'INT UNSIGNED NULL' },
  { table: 'jobs', name: 'reviewed_by',     ddl: 'INT UNSIGNED NULL' },
  { table: 'jobs', name: 'approved_by',     ddl: 'INT UNSIGNED NULL' },
  { table: 'jobs', name: 'decline_reason',  ddl: 'TEXT NULL' },
  // Phase 2a — new permission keys (mirrors the pattern of the 11 existing can_* columns)
  { table: 'permission_overrides', name: 'can_review_job',         ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_approve_job',        ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_manage_departments', ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_manage_admins',      ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_assign_rights',      ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  // Phase 2b — assessment / background-check / deployment
  { table: 'permission_overrides', name: 'can_schedule_assessment',      ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_record_assessment',        ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'permission_overrides', name: 'can_manage_background_checks', ddl: 'TINYINT(1) NOT NULL DEFAULT 0' },
  { table: 'applications', name: 'deployment_station', ddl: 'VARCHAR(255) NULL' },
  { table: 'applications', name: 'deployment_date',    ddl: 'DATE NULL' }
];

// Existing ENUM columns that need more values added — MODIFY is idempotent
// as long as we check first, since re-running an identical MODIFY is harmless
// but we skip it anyway to keep the log output meaningful.
const ENUM_EXPANSIONS = [
  {
    table: 'users', column: 'admin_role',
    ddl: "ENUM('super','hr','recruiter','auditor','hr_officer','it_admin','dhra','hod') NULL",
    containsCheck: 'hod'
  },
  {
    table: 'applications', column: 'status',
    ddl: "ENUM('Pending','Under Review','Shortlisted','Interview','Assessment Scheduled','Assessment Complete','Shortlisted II','Background Check','Offered','Declined','Withdrawn') NOT NULL DEFAULT 'Pending'",
    containsCheck: 'Assessment Scheduled'
  }
];

const TABLES = [
  {
    name: 'departments',
    ddl: `CREATE TABLE IF NOT EXISTS departments (
      id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      name          VARCHAR(100) NOT NULL,
      code          VARCHAR(20)  NOT NULL UNIQUE,
      head_user_id  INT UNSIGNED NULL,
      created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      FOREIGN KEY (head_user_id) REFERENCES users(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
  },
  {
    name: 'assessments',
    ddl: `CREATE TABLE IF NOT EXISTS assessments (
      id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      application_id  INT UNSIGNED NOT NULL,
      type            ENUM('written','psychometric','interview','practical') NOT NULL,
      scheduled_at    DATETIME NULL,
      venue           VARCHAR(255) NULL,
      scheduled_by    INT UNSIGNED NULL,
      score           DECIMAL(5,2) NULL,
      passed          TINYINT(1) NULL,
      notes           TEXT NULL,
      recorded_by     INT UNSIGNED NULL,
      created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      UNIQUE KEY uniq_app_type (application_id, type),
      FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
  },
  {
    name: 'background_checks',
    ddl: `CREATE TABLE IF NOT EXISTS background_checks (
      id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
      application_id  INT UNSIGNED NOT NULL,
      referee_index   TINYINT UNSIGNED NOT NULL,
      referee_name    VARCHAR(255) NULL,
      referee_email   VARCHAR(255) NULL,
      referee_phone   VARCHAR(50)  NULL,
      status          ENUM('pending','contacted','verified','could_not_reach','declined_to_confirm') NOT NULL DEFAULT 'pending',
      notes           TEXT NULL,
      contacted_at    DATETIME NULL,
      contacted_by    INT UNSIGNED NULL,
      created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      UNIQUE KEY uniq_app_referee (application_id, referee_index),
      FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
  }
];

// One-time structural fix: permission_overrides was created with an
// admin_id-keyed schema that no application code has ever actually used —
// every route (permissionsRoutes.js, middleware/rbac.js) has always queried
// and inserted by email/role, which never existed as columns. That means
// every requirePerm() check has been failing closed (500, caught and
// swallowed) since this table was created — not a regression, a bug that
// predates this migration file. Safe to fix in place: the table has 0 rows.
async function fixPermissionOverridesSchema() {
  const [emailCol] = await pool.query(
    `SELECT COLUMN_NAME FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permission_overrides' AND COLUMN_NAME = 'email'`
  );
  if (emailCol.length > 0) {
    console.log('[migrate] permission_overrides.email already exists — skipping structural fix');
    return;
  }
  await pool.query('ALTER TABLE permission_overrides MODIFY COLUMN admin_id INT UNSIGNED NULL');
  await pool.query('ALTER TABLE permission_overrides ADD COLUMN email VARCHAR(255) NULL');
  await pool.query('ALTER TABLE permission_overrides ADD COLUMN role VARCHAR(50) NULL');
  await pool.query('ALTER TABLE permission_overrides ADD UNIQUE KEY uniq_email (email)');
  console.log('[migrate] fixed permission_overrides: admin_id now nullable, added email/role columns with a unique index on email');
}

async function migrate() {
  await fixPermissionOverridesSchema();
  for (const t of TABLES) {
    await pool.query(t.ddl);
    console.log(`[migrate] ensured table ${t.name} exists`);
  }
  for (const e of ENUM_EXPANSIONS) {
    const [rows] = await pool.query(
      `SELECT COLUMN_TYPE FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
      [e.table, e.column]
    );
    if (rows.length === 0) {
      console.log(`[migrate] ${e.table}.${e.column} not found — skipping enum expansion`);
      continue;
    }
    if (rows[0].COLUMN_TYPE.includes(e.containsCheck)) {
      console.log(`[migrate] ${e.table}.${e.column} enum already includes '${e.containsCheck}' — skipping`);
      continue;
    }
    await pool.query(`ALTER TABLE \`${e.table}\` MODIFY COLUMN \`${e.column}\` ${e.ddl}`);
    console.log(`[migrate] expanded enum ${e.table}.${e.column}`);
  }
  for (const col of COLUMNS) {
    const [rows] = await pool.query(
      `SELECT COLUMN_NAME FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`,
      [col.table, col.name]
    );
    if (rows.length > 0) {
      console.log(`[migrate] ${col.table}.${col.name} already exists — skipping`);
      continue;
    }
    await pool.query(`ALTER TABLE \`${col.table}\` ADD COLUMN \`${col.name}\` ${col.ddl}`);
    console.log(`[migrate] added ${col.table}.${col.name}`);
  }
}

migrate()
  .then(() => { console.log('[migrate] done'); process.exit(0); })
  .catch(err => { console.error('[migrate] failed:', err.message); process.exit(1); });

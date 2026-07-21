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
  { table: 'permission_overrides', name: 'can_assign_rights',      ddl: 'TINYINT(1) NOT NULL DEFAULT 0' }
];

// Existing ENUM columns that need more values added — MODIFY is idempotent
// as long as we check first, since re-running an identical MODIFY is harmless
// but we skip it anyway to keep the log output meaningful.
const ENUM_EXPANSIONS = [
  {
    table: 'users', column: 'admin_role',
    ddl: "ENUM('super','hr','recruiter','auditor','hr_officer','it_admin','dhra','hod') NULL",
    containsCheck: 'hod'
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
  }
];

async function migrate() {
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

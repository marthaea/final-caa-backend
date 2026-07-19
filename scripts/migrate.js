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
  { table: 'users', name: 'reset_token_expires', ddl: 'DATETIME NULL' }
];

async function migrate() {
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

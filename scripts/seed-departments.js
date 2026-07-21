// scripts/seed-departments.js — backfills the departments table from the
// department names already in use across jobs/staff, so the new
// departments-managed dropdown doesn't lose anything that's already live.
// Non-destructive: only inserts rows for codes that don't already exist.
//
// Usage: node scripts/seed-departments.js

require('dotenv').config();
const pool = require('../config/db');

const DEPARTMENTS = [
  { name: 'Air Traffic Mgmt',   code: 'ATM' },
  { name: 'Aviation Safety',    code: 'SAFETY' },
  { name: 'ICT & Systems',      code: 'ICT' },
  { name: 'Finance & Admin',    code: 'FINANCE' },
  { name: 'Legal',              code: 'LEGAL' },
  { name: 'Operations',         code: 'OPS' },
  { name: 'Human Resources',    code: 'HR' },
  { name: 'Procurement',        code: 'PROC' },
  { name: 'Engineering',        code: 'ENG' },
  { name: 'Communications',     code: 'COMM' },
];

async function main() {
  console.log(`Connecting to ${process.env.DB_NAME}@${process.env.DB_HOST} ...`);
  let added = 0, skipped = 0;

  for (const d of DEPARTMENTS) {
    const [existing] = await pool.query(
      'SELECT id FROM departments WHERE code = ?', [d.code]
    );
    if (existing.length > 0) {
      skipped++;
      continue;
    }
    await pool.query('INSERT INTO departments (name, code) VALUES (?, ?)', [d.name, d.code]);
    console.log(`  added ${d.code} — ${d.name}`);
    added++;
  }

  console.log(`Done. ${added} added, ${skipped} already existed.`);
  process.exit(0);
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});

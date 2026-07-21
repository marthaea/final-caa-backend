// scripts/backfill-job-departments.js — links existing jobs to the real
// departments table by matching jobs.dept (free-text name) against
// departments.name. Only touches jobs where department_id is still NULL, so
// it's safe to re-run.
//
// Usage: node scripts/backfill-job-departments.js

require('dotenv').config();
const pool = require('../config/db');

async function main() {
  console.log(`Connecting to ${process.env.DB_NAME}@${process.env.DB_HOST} ...`);

  const [jobs] = await pool.query('SELECT id, dept FROM jobs WHERE department_id IS NULL');
  const [depts] = await pool.query('SELECT id, name FROM departments');
  const byName = new Map(depts.map((d) => [d.name.trim().toLowerCase(), d.id]));

  let linked = 0, unmatched = 0;
  for (const job of jobs) {
    const deptId = byName.get((job.dept || '').trim().toLowerCase());
    if (!deptId) {
      console.log(`  no match for job ${job.id} — dept "${job.dept}"`);
      unmatched++;
      continue;
    }
    await pool.query('UPDATE jobs SET department_id = ? WHERE id = ?', [deptId, job.id]);
    linked++;
  }

  console.log(`Done. ${linked} jobs linked, ${unmatched} unmatched (left NULL — assign manually via Edit).`);
  process.exit(0);
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});

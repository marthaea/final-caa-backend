// scripts/seed-admins.js — inserts the 3 documented HR/admin accounts into the
// `users` table if they don't already exist. Unlike seed.js, this never
// truncates or deletes anything — it only adds missing rows, so it's safe to
// run against a database that already has real jobs/candidates in it.
//
// Usage: node scripts/seed-admins.js

require('dotenv').config();
const bcrypt = require('bcrypt');
const pool = require('../config/db');

const SALT_ROUNDS = 12;

const ADMIN_USERS = [
  { email: 'admin@caa.go.ug',      plainPassword: 'Admin@2026',   first_name: 'Alex',  last_name: 'Mukasa',   admin_role: 'super' },
  { email: 'hrdirector@caa.go.ug', plainPassword: 'HrDir@2026',   first_name: 'Jane',  last_name: 'Mirembe',  admin_role: 'hr' },
  { email: 'recruit@caa.go.ug',    plainPassword: 'Recruit@2026', first_name: 'David', last_name: 'Ssempala', admin_role: 'recruiter' },
];

async function main() {
  console.log(`Connecting to ${process.env.DB_NAME}@${process.env.DB_HOST} ...`);

  for (const u of ADMIN_USERS) {
    const [existing] = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER(?)', [u.email]
    );
    if (existing.length > 0) {
      console.log(`  skip  ${u.email} (already exists, id=${existing[0].id})`);
      continue;
    }
    const hash = await bcrypt.hash(u.plainPassword, SALT_ROUNDS);
    await pool.query(
      `INSERT INTO users (email, password_hash, first_name, last_name, account_type, admin_role, effective_type, email_verified)
       VALUES (?, ?, ?, ?, 'admin', ?, 'admin', 1)`,
      [u.email, hash, u.first_name, u.last_name, u.admin_role]
    );
    console.log(`  added ${u.email} (role: ${u.admin_role})`);
  }

  console.log('Done.');
  process.exit(0);
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});

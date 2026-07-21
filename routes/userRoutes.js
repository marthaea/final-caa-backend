const router = require('express').Router();
const bcrypt = require('bcrypt');
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requireRole } = require('../middleware/rbac');
const { ok, okList, fail, logAudit, checkPerm } = require('../utils/format');
const { ADMIN_ROLES } = require('../config/constants');

const SALT_ROUNDS = 12;

function mapAdminUser(row) {
  return {
    id: row.id,
    email: row.email,
    firstName: row.first_name,
    lastName: row.last_name,
    adminRole: row.admin_role,
    isActive: !!row.is_active
  };
}

// GET /api/users/admin — real admin accounts, for the Assign Rights picker
// and the HOD dropdown when assigning a department head (previously this
// list was a hardcoded 3-account demo dict on the frontend).
router.get('/admin', verifyToken, asyncHandler(async (req, res) => {
  const allowed = await checkPerm(pool, req.user, 'canManageAdmins') || await checkPerm(pool, req.user, 'canAssignRights');
  if (!allowed) return fail(res, 'Permission denied', 403);

  const [rows] = await pool.query(
    "SELECT * FROM users WHERE account_type = 'admin' ORDER BY first_name, last_name"
  );
  return okList(res, rows.map(mapAdminUser));
}));

// POST /api/users/admin — create a new admin account with an assigned role.
router.post('/admin', verifyToken, requireRole('super'), asyncHandler(async (req, res) => {
  const { email, password, firstName, lastName, adminRole } = req.body;

  if (!email || !password || !firstName || !lastName || !adminRole) {
    return fail(res, 'Email, password, first name, last name, and role are required');
  }
  if (!ADMIN_ROLES.includes(adminRole)) {
    return fail(res, `adminRole must be one of: ${ADMIN_ROLES.join(', ')}`);
  }
  if (password.length < 8) {
    return fail(res, 'Password must be at least 8 characters');
  }

  const [existing] = await pool.query('SELECT id FROM users WHERE email = ?', [email]);
  if (existing.length > 0) {
    return fail(res, 'A user with this email already exists', 409);
  }

  const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
  const [result] = await pool.query(
    `INSERT INTO users (email, password_hash, first_name, last_name, account_type, admin_role, effective_type, email_verified)
     VALUES (?, ?, ?, ?, 'admin', ?, 'admin', 1)`,
    [email, passwordHash, firstName, lastName, adminRole]
  );

  const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [result.insertId]);
  await logAudit(pool, req, 'Created admin account', `${firstName} ${lastName} (${email}, ${adminRole})`);
  return ok(res, mapAdminUser(rows[0]), 201);
}));

module.exports = router;

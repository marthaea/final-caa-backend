// controllers/userController.js
const pool = require('../config/db');

// GET /api/v1/users/me
async function getMe(req, res) {
  const [rows] = await pool.query(
    `SELECT id, email, role, is_active, created_at FROM users WHERE id = ?`,
    [req.user.id]
  );
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'User not found' });
  }
  res.json({ status: 'ok', user: rows[0] });
}

// PUT /api/v1/users/me
async function updateMe(req, res) {
  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ status: 'error', message: 'Email required' });
  }
  await pool.query(`UPDATE users SET email = ? WHERE id = ?`, [email, req.user.id]);
  res.json({ status: 'ok', message: 'User updated' });
}

// GET /api/v1/users/internal (HR Director, Super Admin)
async function listInternalStaff(req, res) {
  const [rows] = await pool.query(
    `SELECT id, email, role, is_active, created_at FROM users
     WHERE role IN ('recruiter', 'hr_director', 'super_admin', 'internal_candidate')
     ORDER BY created_at DESC`
  );
  res.json({ status: 'ok', users: rows });
}

// GET /api/v1/users/:id (Super Admin)
async function getUserById(req, res) {
  const [rows] = await pool.query(
    `SELECT id, email, role, is_active, created_at FROM users WHERE id = ?`,
    [req.params.id]
  );
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'User not found' });
  }
  res.json({ status: 'ok', user: rows[0] });
}

// PUT /api/v1/users/:id/role (Super Admin)
async function updateUserRole(req, res) {
  const { role } = req.body;
  const validRoles = ['external_candidate', 'internal_candidate', 'recruiter', 'hr_director', 'super_admin'];
  if (!validRoles.includes(role)) {
    return res.status(400).json({ status: 'error', message: 'Invalid role' });
  }
  await pool.query(`UPDATE users SET role = ? WHERE id = ?`, [role, req.params.id]);
  res.json({ status: 'ok', message: 'Role updated' });
}

// PUT /api/v1/users/:id/status (Super Admin) - activate/deactivate
async function updateUserStatus(req, res) {
  const { is_active } = req.body;
  if (typeof is_active !== 'boolean') {
    return res.status(400).json({ status: 'error', message: 'is_active must be boolean' });
  }
  await pool.query(`UPDATE users SET is_active = ? WHERE id = ?`, [is_active, req.params.id]);
  res.json({ status: 'ok', message: 'User status updated' });
}

module.exports = {
  getMe,
  updateMe,
  listInternalStaff,
  getUserById,
  updateUserRole,
  updateUserStatus
};

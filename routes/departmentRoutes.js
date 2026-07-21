const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit, checkPerm } = require('../utils/format');

function mapDept(row) {
  return {
    id: row.id,
    name: row.name,
    code: row.code,
    headUserId: row.head_user_id
  };
}

// GET /api/departments — needed both by job creators (dept dropdown) and by
// whoever manages departments/HOD assignment, so either permission suffices.
router.get('/', verifyToken, asyncHandler(async (req, res) => {
  const allowed = await checkPerm(pool, req.user, 'canManageJobs') || await checkPerm(pool, req.user, 'canManageDepartments');
  if (!allowed) return fail(res, 'Permission denied', 403);
  const [rows] = await pool.query('SELECT * FROM departments ORDER BY name ASC');
  return okList(res, rows.map(mapDept));
}));

// POST /api/departments
router.post('/', verifyToken, requirePerm('canManageDepartments'), asyncHandler(async (req, res) => {
  const { name, code } = req.body;
  if (!name || !String(name).trim() || !code || !String(code).trim()) {
    return fail(res, 'Department name and code are required');
  }

  const [existing] = await pool.query(
    'SELECT id FROM departments WHERE code = ?', [code]
  );
  if (existing.length > 0) {
    return fail(res, 'A department with this code already exists', 409);
  }

  const [result] = await pool.query(
    'INSERT INTO departments (name, code) VALUES (?, ?)',
    [name, code]
  );

  const [rows] = await pool.query('SELECT * FROM departments WHERE id = ?', [result.insertId]);
  await logAudit(pool, req, 'Added department', `${name} (${code})`);
  return ok(res, mapDept(rows[0]), 201);
}));

// PUT /api/departments/:id — currently used to assign/change the Head of Department
router.put('/:id', verifyToken, requirePerm('canManageDepartments'), asyncHandler(async (req, res) => {
  const { headUserId } = req.body;
  const [existing] = await pool.query('SELECT * FROM departments WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Department not found', 404);

  if (headUserId != null) {
    const [userRows] = await pool.query(
      "SELECT id FROM users WHERE id = ? AND account_type = 'admin' AND admin_role = 'hod'", [headUserId]
    );
    if (userRows.length === 0) return fail(res, 'headUserId must be an existing admin user with the Head of Department role');
  }

  await pool.query('UPDATE departments SET head_user_id = ? WHERE id = ?', [headUserId ?? null, req.params.id]);
  const [rows] = await pool.query('SELECT * FROM departments WHERE id = ?', [req.params.id]);
  await logAudit(pool, req, 'Assigned department head', `${existing[0].name} (${existing[0].code})`);
  return ok(res, mapDept(rows[0]));
}));

module.exports = router;

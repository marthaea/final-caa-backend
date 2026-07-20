const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit } = require('../utils/format');

function mapStaff(row) {
  return {
    id: row.id,
    empNo: row.employee_number,
    firstName: row.first_name,
    lastName: row.last_name,
    dept: row.dept,
    position: row.position,
    email: row.email,
    joined: row.joined_date instanceof Date
      ? row.joined_date.toISOString().slice(0, 10)
      : String(row.joined_date),
    status: row.status
  };
}

// GET /api/staff
router.get('/', verifyToken, requirePerm('canViewStaff'), asyncHandler(async (req, res) => {
  const { search } = req.query;
  let sql = `SELECT * FROM staff`;
  const params = [];
  if (search) {
    sql += ` WHERE first_name LIKE ? OR last_name LIKE ? OR email LIKE ? OR dept LIKE ?`;
    const like = `%${search}%`;
    params.push(like, like, like, like);
  }
  sql += ' ORDER BY last_name ASC';
  const [rows] = await pool.query(sql, params);
  return okList(res, rows.map(mapStaff));
}));

// POST /api/staff
router.post('/', verifyToken, requirePerm('canViewStaff'), asyncHandler(async (req, res) => {
  const { employeeNumber, firstName, lastName, dept, position, email, joined, status } = req.body;

  if (!employeeNumber || !String(employeeNumber).trim() || !firstName || !lastName) {
    return fail(res, 'Employee number, first name, and last name are required');
  }

  const [existing] = await pool.query(
    'SELECT id FROM staff WHERE employee_number = ?', [employeeNumber]
  );
  if (existing.length > 0) {
    return fail(res, 'A staff record with this employee number already exists', 409);
  }

  const [result] = await pool.query(
    `INSERT INTO staff (employee_number, first_name, last_name, dept, position, email, joined_date, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [employeeNumber, firstName, lastName, dept || null, position || null, email || null, joined || null, status || 'Active']
  );

  const [rows] = await pool.query('SELECT * FROM staff WHERE id = ?', [result.insertId]);
  await logAudit(pool, req, 'Added staff record', `${firstName} ${lastName} (${employeeNumber})`);
  return ok(res, mapStaff(rows[0]), 201);
}));

// GET /api/staff/verify/:employeeNumber  (public — needed before account exists)
router.get('/verify/:employeeNumber', asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT id FROM staff WHERE employee_number = ? LIMIT 1',
    [req.params.employeeNumber]
  );
  return res.json({ exists: rows.length > 0 });
}));

module.exports = router;

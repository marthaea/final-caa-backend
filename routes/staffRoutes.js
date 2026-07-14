const router = require('express').Router();
const pool = require('../config/db');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail } = require('../utils/format');

function mapStaff(row) {
  return {
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
router.get('/', verifyToken, requirePerm('canViewStaff'), async (req, res) => {
  try {
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
  } catch (e) {
    console.error('GET /staff:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// GET /api/staff/verify/:employeeNumber  (public — needed before account exists)
router.get('/verify/:employeeNumber', async (req, res) => {
  try {
    const [rows] = await pool.query(
      'SELECT id FROM staff WHERE employee_number = ? LIMIT 1',
      [req.params.employeeNumber]
    );
    return res.json({ exists: rows.length > 0 });
  } catch (e) {
    console.error('GET /staff/verify:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

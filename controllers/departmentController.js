// controllers/departmentController.js
const pool = require('../config/db');
const { generateNextDeptCode } = require('../utils/deptId');

// GET /api/v1/departments
async function listDepartments(req, res) {
  const [rows] = await pool.query(`SELECT * FROM departments ORDER BY id ASC`);
  res.json({ status: 'ok', departments: rows });
}

// GET /api/v1/departments/:id
async function getDepartment(req, res) {
  const [rows] = await pool.query(`SELECT * FROM departments WHERE id = ?`, [req.params.id]);
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'Department not found' });
  }
  res.json({ status: 'ok', department: rows[0] });
}

// POST /api/v1/departments (HR Director, Super Admin)
// dept_code is auto-generated here (DEPT-001 style), not supplied by the client.
async function createDepartment(req, res) {
  const { name } = req.body;
  if (!name) {
    return res.status(400).json({ status: 'error', message: 'Department name required' });
  }

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    // Lock the table briefly via SELECT ... FOR UPDATE pattern to avoid
    // two concurrent requests generating the same DEPT-XXX code.
    const [rows] = await connection.query(
      `SELECT dept_code FROM departments ORDER BY id DESC LIMIT 1 FOR UPDATE`
    );
    const dept_code = rows.length === 0
      ? 'DEPT-001'
      : (() => {
          const lastNumber = parseInt(rows[0].dept_code.split('-')[1], 10);
          return `DEPT-${String(lastNumber + 1).padStart(3, '0')}`;
        })();

    const [result] = await connection.query(
      `INSERT INTO departments (dept_code, name) VALUES (?, ?)`,
      [dept_code, name]
    );

    await connection.commit();
    res.status(201).json({ status: 'ok', department_id: result.insertId, dept_code });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// PUT /api/v1/departments/:id (HR Director, Super Admin)
async function updateDepartment(req, res) {
  const { name } = req.body;
  if (!name) {
    return res.status(400).json({ status: 'error', message: 'Department name required' });
  }
  await pool.query(`UPDATE departments SET name = ? WHERE id = ?`, [name, req.params.id]);
  res.json({ status: 'ok', message: 'Department updated' });
}

// DELETE /api/v1/departments/:id (Super Admin)
async function deleteDepartment(req, res) {
  await pool.query(`DELETE FROM departments WHERE id = ?`, [req.params.id]);
  res.json({ status: 'ok', message: 'Department deleted' });
}

module.exports = {
  listDepartments,
  getDepartment,
  createDepartment,
  updateDepartment,
  deleteDepartment
};

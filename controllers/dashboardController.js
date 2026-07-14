// controllers/dashboardController.js
const pool = require('../config/db');

// GET /api/v1/dashboard/summary
async function getSummary(req, res) {
  const [[{ total_applications }]] = await pool.query(`SELECT COUNT(*) AS total_applications FROM applications`);
  const [[{ pending_review }]] = await pool.query(
    `SELECT COUNT(*) AS pending_review FROM applications WHERE status = 'submitted'`
  );
  const [[{ shortlisted }]] = await pool.query(
    `SELECT COUNT(*) AS shortlisted FROM applications WHERE status = 'shortlisted'`
  );
  const [[{ open_vacancies }]] = await pool.query(
    `SELECT COUNT(*) AS open_vacancies FROM vacancies WHERE status = 'open'`
  );
  const [[{ closing_soon }]] = await pool.query(
    `SELECT COUNT(*) AS closing_soon FROM vacancies
     WHERE status = 'open' AND closing_date IS NOT NULL
       AND closing_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)`
  );

  res.json({
    status: 'ok',
    summary: { total_applications, pending_review, shortlisted, open_vacancies, closing_soon }
  });
}

// GET /api/v1/dashboard/applications-by-status
async function getApplicationsByStatus(req, res) {
  const [rows] = await pool.query(
    `SELECT status, COUNT(*) AS count FROM applications GROUP BY status`
  );
  res.json({ status: 'ok', data: rows });
}

// GET /api/v1/dashboard/applications-by-department
async function getApplicationsByDepartment(req, res) {
  const [rows] = await pool.query(
    `SELECT d.name AS department, COUNT(a.id) AS count
     FROM applications a
     JOIN vacancies v ON v.id = a.vacancy_id
     JOIN departments d ON d.id = v.department_id
     GROUP BY d.name`
  );
  res.json({ status: 'ok', data: rows });
}

// GET /api/v1/dashboard/application-trend
async function getApplicationTrend(req, res) {
  const [rows] = await pool.query(
    `SELECT DATE(applied_at) AS date, COUNT(*) AS count
     FROM applications
     WHERE applied_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
     GROUP BY DATE(applied_at)
     ORDER BY date ASC`
  );
  res.json({ status: 'ok', data: rows });
}

module.exports = {
  getSummary,
  getApplicationsByStatus,
  getApplicationsByDepartment,
  getApplicationTrend
};

// controllers/vacancyController.js
const pool = require('../config/db');

// GET /api/v1/vacancies
// Public callers (no req.user, or external_candidate) only ever see open, non-internal vacancies.
// Internal candidates additionally see open internal-only vacancies.
// Recruiter/HR Director/Super Admin see everything and can filter by status/department/type.
async function listVacancies(req, res) {
  const { department_id, location, type, status, is_internal } = req.query;
  const conditions = [];
  const params = [];

  const role = req.user ? req.user.role : null;
  const privilegedRoles = ['recruiter', 'hr_director', 'super_admin'];

  if (!role || role === 'external_candidate') {
    conditions.push(`status = 'open'`, `is_internal = FALSE`);
  } else if (role === 'internal_candidate') {
    conditions.push(`status = 'open'`);
  } else if (privilegedRoles.includes(role)) {
    if (status) {
      conditions.push(`status = ?`);
      params.push(status);
    }
    if (typeof is_internal !== 'undefined') {
      conditions.push(`is_internal = ?`);
      params.push(is_internal === 'true');
    }
  }

  if (department_id) {
    conditions.push(`department_id = ?`);
    params.push(department_id);
  }
  if (location) {
    conditions.push(`location LIKE ?`);
    params.push(`%${location}%`);
  }
  if (type) {
    conditions.push(`type = ?`);
    params.push(type);
  }

  const whereClause = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const [rows] = await pool.query(
    `SELECT * FROM vacancies ${whereClause} ORDER BY created_at DESC`,
    params
  );

  res.json({ status: 'ok', vacancies: rows });
}

// GET /api/v1/vacancies/:id
async function getVacancy(req, res) {
  const [rows] = await pool.query(`SELECT * FROM vacancies WHERE id = ?`, [req.params.id]);
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
  }

  const vacancy = rows[0];
  const role = req.user ? req.user.role : null;
  const privilegedRoles = ['recruiter', 'hr_director', 'super_admin'];

  // Enforce visibility for non-privileged callers
  if (!privilegedRoles.includes(role)) {
    if (vacancy.status !== 'open') {
      return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
    }
    if (vacancy.is_internal && role !== 'internal_candidate') {
      return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
    }
  }

  res.json({ status: 'ok', vacancy });
}

// POST /api/v1/vacancies (HR Director, Super Admin)
async function createVacancy(req, res) {
  const { title, department_id, description, requirements, location, type, is_internal, closing_date } = req.body;

  if (!title || !department_id || !type) {
    return res.status(400).json({ status: 'error', message: 'title, department_id and type are required' });
  }

  const [result] = await pool.query(
    `INSERT INTO vacancies
       (title, department_id, description, requirements, location, type, is_internal, status, posted_by, closing_date)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?)`,
    [title, department_id, description || null, requirements || null, location || null, type,
      !!is_internal, req.user.id, closing_date || null]
  );

  res.status(201).json({ status: 'ok', vacancy_id: result.insertId });
}

// PUT /api/v1/vacancies/:id (HR Director, Super Admin)
async function updateVacancy(req, res) {
  const { title, department_id, description, requirements, location, type, is_internal, closing_date } = req.body;

  const [result] = await pool.query(
    `UPDATE vacancies
     SET title = COALESCE(?, title),
         department_id = COALESCE(?, department_id),
         description = COALESCE(?, description),
         requirements = COALESCE(?, requirements),
         location = COALESCE(?, location),
         type = COALESCE(?, type),
         is_internal = COALESCE(?, is_internal),
         closing_date = COALESCE(?, closing_date)
     WHERE id = ?`,
    [title, department_id, description, requirements, location, type, is_internal, closing_date, req.params.id]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
  }
  res.json({ status: 'ok', message: 'Vacancy updated' });
}

// DELETE /api/v1/vacancies/:id (HR Director, Super Admin)
async function deleteVacancy(req, res) {
  const [result] = await pool.query(`DELETE FROM vacancies WHERE id = ?`, [req.params.id]);
  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
  }
  res.json({ status: 'ok', message: 'Vacancy deleted' });
}

// POST /api/v1/vacancies/:id/publish
async function publishVacancy(req, res) {
  const [result] = await pool.query(`UPDATE vacancies SET status = 'open' WHERE id = ?`, [req.params.id]);
  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
  }
  res.json({ status: 'ok', message: 'Vacancy published' });
}

// POST /api/v1/vacancies/:id/close
async function closeVacancy(req, res) {
  const [result] = await pool.query(`UPDATE vacancies SET status = 'closed' WHERE id = ?`, [req.params.id]);
  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Vacancy not found' });
  }
  res.json({ status: 'ok', message: 'Vacancy closed' });
}

module.exports = {
  listVacancies,
  getVacancy,
  createVacancy,
  updateVacancy,
  deleteVacancy,
  publishVacancy,
  closeVacancy
};

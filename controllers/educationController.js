// controllers/educationController.js
const pool = require('../config/db');

async function getProfileId(userId) {
  const [rows] = await pool.query(`SELECT id FROM candidate_profiles WHERE user_id = ?`, [userId]);
  return rows.length ? rows[0].id : null;
}

// GET /api/v1/candidate-profiles/me/education
async function listEducation(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [rows] = await pool.query(`SELECT * FROM education WHERE candidate_profile_id = ?`, [profileId]);
  res.json({ status: 'ok', education: rows });
}

// POST /api/v1/candidate-profiles/me/education
async function addEducation(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { institution, degree, field_of_study, start_date, end_date } = req.body;
  if (!institution) {
    return res.status(400).json({ status: 'error', message: 'Institution required' });
  }

  const [result] = await pool.query(
    `INSERT INTO education (candidate_profile_id, institution, degree, field_of_study, start_date, end_date)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [profileId, institution, degree || null, field_of_study || null, start_date || null, end_date || null]
  );

  res.status(201).json({ status: 'ok', education_id: result.insertId });
}

// PUT /api/v1/candidate-profiles/me/education/:id
async function updateEducation(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { institution, degree, field_of_study, start_date, end_date } = req.body;

  const [result] = await pool.query(
    `UPDATE education
     SET institution = COALESCE(?, institution),
         degree = COALESCE(?, degree),
         field_of_study = COALESCE(?, field_of_study),
         start_date = COALESCE(?, start_date),
         end_date = COALESCE(?, end_date)
     WHERE id = ? AND candidate_profile_id = ?`,
    [institution, degree, field_of_study, start_date, end_date, req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Education entry not found' });
  }
  res.json({ status: 'ok', message: 'Education entry updated' });
}

// DELETE /api/v1/candidate-profiles/me/education/:id
async function deleteEducation(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [result] = await pool.query(
    `DELETE FROM education WHERE id = ? AND candidate_profile_id = ?`,
    [req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Education entry not found' });
  }
  res.json({ status: 'ok', message: 'Education entry deleted' });
}

module.exports = { listEducation, addEducation, updateEducation, deleteEducation };

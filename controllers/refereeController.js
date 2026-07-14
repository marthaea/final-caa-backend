// controllers/refereeController.js
const pool = require('../config/db');

async function getProfileId(userId) {
  const [rows] = await pool.query(`SELECT id FROM candidate_profiles WHERE user_id = ?`, [userId]);
  return rows.length ? rows[0].id : null;
}

// GET /api/v1/candidate-profiles/me/referees
async function listReferees(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [rows] = await pool.query(`SELECT * FROM referees WHERE candidate_profile_id = ?`, [profileId]);
  res.json({ status: 'ok', referees: rows });
}

// POST /api/v1/candidate-profiles/me/referees
async function addReferee(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { name, relationship, phone, email } = req.body;
  if (!name) {
    return res.status(400).json({ status: 'error', message: 'Referee name required' });
  }

  const [result] = await pool.query(
    `INSERT INTO referees (candidate_profile_id, name, relationship, phone, email)
     VALUES (?, ?, ?, ?, ?)`,
    [profileId, name, relationship || null, phone || null, email || null]
  );

  res.status(201).json({ status: 'ok', referee_id: result.insertId });
}

// PUT /api/v1/candidate-profiles/me/referees/:id
async function updateReferee(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { name, relationship, phone, email } = req.body;

  const [result] = await pool.query(
    `UPDATE referees
     SET name = COALESCE(?, name),
         relationship = COALESCE(?, relationship),
         phone = COALESCE(?, phone),
         email = COALESCE(?, email)
     WHERE id = ? AND candidate_profile_id = ?`,
    [name, relationship, phone, email, req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Referee not found' });
  }
  res.json({ status: 'ok', message: 'Referee updated' });
}

// DELETE /api/v1/candidate-profiles/me/referees/:id
async function deleteReferee(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [result] = await pool.query(
    `DELETE FROM referees WHERE id = ? AND candidate_profile_id = ?`,
    [req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Referee not found' });
  }
  res.json({ status: 'ok', message: 'Referee deleted' });
}

module.exports = { listReferees, addReferee, updateReferee, deleteReferee };

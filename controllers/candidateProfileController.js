// controllers/candidateProfileController.js
const pool = require('../config/db');

// GET /api/v1/candidate-profiles/me
async function getMyProfile(req, res) {
  const [rows] = await pool.query(
    `SELECT * FROM candidate_profiles WHERE user_id = ?`,
    [req.user.id]
  );
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'Profile not found' });
  }
  res.json({ status: 'ok', profile: rows[0] });
}

// PUT /api/v1/candidate-profiles/me
async function updateMyProfile(req, res) {
  const { first_name, last_name, phone, address } = req.body;

  await pool.query(
    `UPDATE candidate_profiles
     SET first_name = COALESCE(?, first_name),
         last_name = COALESCE(?, last_name),
         phone = COALESCE(?, phone),
         address = COALESCE(?, address)
     WHERE user_id = ?`,
    [first_name, last_name, phone, address, req.user.id]
  );

  res.json({ status: 'ok', message: 'Profile updated' });
}

// POST /api/v1/candidate-profiles/me/resume
// Expects file upload middleware (e.g. multer) upstream to place the file
// and provide req.file.path. Path storage logic only, here.
async function uploadResume(req, res) {
  if (!req.file) {
    return res.status(400).json({ status: 'error', message: 'No file uploaded' });
  }
  await pool.query(
    `UPDATE candidate_profiles SET resume_path = ? WHERE user_id = ?`,
    [req.file.path, req.user.id]
  );
  res.json({ status: 'ok', message: 'Resume uploaded', path: req.file.path });
}

// POST /api/v1/candidate-profiles/me/cover-letter
async function uploadCoverLetter(req, res) {
  if (!req.file) {
    return res.status(400).json({ status: 'error', message: 'No file uploaded' });
  }
  await pool.query(
    `UPDATE candidate_profiles SET cover_letter_path = ? WHERE user_id = ?`,
    [req.file.path, req.user.id]
  );
  res.json({ status: 'ok', message: 'Cover letter uploaded', path: req.file.path });
}

// GET /api/v1/candidate-profiles/:id (Recruiter, HR Director, Super Admin)
async function getProfileById(req, res) {
  const [rows] = await pool.query(`SELECT * FROM candidate_profiles WHERE id = ?`, [req.params.id]);
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'Profile not found' });
  }
  res.json({ status: 'ok', profile: rows[0] });
}

module.exports = {
  getMyProfile,
  updateMyProfile,
  uploadResume,
  uploadCoverLetter,
  getProfileById
};

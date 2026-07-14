// controllers/certificateController.js
const pool = require('../config/db');

async function getProfileId(userId) {
  const [rows] = await pool.query(`SELECT id FROM candidate_profiles WHERE user_id = ?`, [userId]);
  return rows.length ? rows[0].id : null;
}

// GET /api/v1/candidate-profiles/me/certificates
async function listCertificates(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [rows] = await pool.query(`SELECT * FROM professional_certificates WHERE candidate_profile_id = ?`, [profileId]);
  res.json({ status: 'ok', certificates: rows });
}

// POST /api/v1/candidate-profiles/me/certificates
async function addCertificate(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { name, issuing_organization, issue_date, expiry_date, credential_url } = req.body;
  if (!name) {
    return res.status(400).json({ status: 'error', message: 'Certificate name required' });
  }

  const [result] = await pool.query(
    `INSERT INTO professional_certificates
       (candidate_profile_id, name, issuing_organization, issue_date, expiry_date, credential_url)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [profileId, name, issuing_organization || null, issue_date || null, expiry_date || null, credential_url || null]
  );

  res.status(201).json({ status: 'ok', certificate_id: result.insertId });
}

// PUT /api/v1/candidate-profiles/me/certificates/:id
async function updateCertificate(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const { name, issuing_organization, issue_date, expiry_date, credential_url } = req.body;

  const [result] = await pool.query(
    `UPDATE professional_certificates
     SET name = COALESCE(?, name),
         issuing_organization = COALESCE(?, issuing_organization),
         issue_date = COALESCE(?, issue_date),
         expiry_date = COALESCE(?, expiry_date),
         credential_url = COALESCE(?, credential_url)
     WHERE id = ? AND candidate_profile_id = ?`,
    [name, issuing_organization, issue_date, expiry_date, credential_url, req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Certificate not found' });
  }
  res.json({ status: 'ok', message: 'Certificate updated' });
}

// DELETE /api/v1/candidate-profiles/me/certificates/:id
async function deleteCertificate(req, res) {
  const profileId = await getProfileId(req.user.id);
  if (!profileId) return res.status(404).json({ status: 'error', message: 'Profile not found' });

  const [result] = await pool.query(
    `DELETE FROM professional_certificates WHERE id = ? AND candidate_profile_id = ?`,
    [req.params.id, profileId]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Certificate not found' });
  }
  res.json({ status: 'ok', message: 'Certificate deleted' });
}

module.exports = { listCertificates, addCertificate, updateCertificate, deleteCertificate };

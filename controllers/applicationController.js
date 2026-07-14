// controllers/applicationController.js
const pool = require('../config/db');

async function getProfileId(userId) {
  const [rows] = await pool.query(`SELECT id FROM candidate_profiles WHERE user_id = ?`, [userId]);
  return rows.length ? rows[0].id : null;
}

// Evaluates a submitted application against the vacancy's shortlist_criteria.
// Per project decision: no numeric score is stored — this simply flips
// status straight to 'shortlisted' if the candidate passes the criteria,
// otherwise leaves it at 'submitted' for manual review.
//
// NOTE: criterion matching here is intentionally simple (keyword presence
// against education/certificates) as a starting point — swap in whatever
// matching rules the recruiter team defines.
async function evaluateAutoShortlist(connection, applicationId, vacancyId, candidateProfileId) {
  const [criteriaRows] = await connection.query(
    `SELECT * FROM shortlist_criteria WHERE vacancy_id = ?`,
    [vacancyId]
  );

  if (criteriaRows.length === 0) {
    return false; // no criteria defined, leave for manual review
  }

  const [educationRows] = await connection.query(
    `SELECT * FROM education WHERE candidate_profile_id = ?`,
    [candidateProfileId]
  );
  const [certRows] = await connection.query(
    `SELECT * FROM professional_certificates WHERE candidate_profile_id = ?`,
    [candidateProfileId]
  );

  const searchableText = [
    ...educationRows.map(e => `${e.degree} ${e.field_of_study} ${e.institution}`),
    ...certRows.map(c => `${c.name} ${c.issuing_organization}`)
  ].join(' ').toLowerCase();

  const allCriteriaMet = criteriaRows.every(c =>
    searchableText.includes(c.criterion.toLowerCase())
  );

  if (allCriteriaMet) {
    await connection.query(
      `UPDATE applications SET status = 'shortlisted' WHERE id = ?`,
      [applicationId]
    );
    return true;
  }

  return false;
}

// POST /api/v1/vacancies/:vacancy_id/apply
async function applyToVacancy(req, res) {
  const { vacancy_id } = req.params;
  const candidateProfileId = await getProfileId(req.user.id);
  if (!candidateProfileId) {
    return res.status(404).json({ status: 'error', message: 'Candidate profile not found' });
  }

  const [vacancyRows] = await pool.query(`SELECT * FROM vacancies WHERE id = ?`, [vacancy_id]);
  const vacancy = vacancyRows[0];
  if (!vacancy || vacancy.status !== 'open') {
    return res.status(400).json({ status: 'error', message: 'Vacancy is not open for applications' });
  }
  if (vacancy.is_internal && req.user.role !== 'internal_candidate') {
    return res.status(403).json({ status: 'error', message: 'This vacancy is internal-only' });
  }

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [result] = await connection.query(
      `INSERT INTO applications (vacancy_id, candidate_profile_id, status) VALUES (?, ?, 'submitted')`,
      [vacancy_id, candidateProfileId]
    );

    const shortlisted = await evaluateAutoShortlist(connection, result.insertId, vacancy_id, candidateProfileId);

    await connection.commit();
    res.status(201).json({
      status: 'ok',
      application_id: result.insertId,
      auto_shortlisted: shortlisted
    });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// GET /api/v1/applications/me
async function listMyApplications(req, res) {
  const candidateProfileId = await getProfileId(req.user.id);
  if (!candidateProfileId) {
    return res.status(404).json({ status: 'error', message: 'Candidate profile not found' });
  }

  const [rows] = await pool.query(
    `SELECT a.*, v.title AS vacancy_title, v.status AS vacancy_status
     FROM applications a
     JOIN vacancies v ON v.id = a.vacancy_id
     WHERE a.candidate_profile_id = ?
     ORDER BY a.applied_at DESC`,
    [candidateProfileId]
  );

  res.json({ status: 'ok', applications: rows });
}

// PUT /api/v1/applications/me/:id/withdraw
async function withdrawApplication(req, res) {
  const candidateProfileId = await getProfileId(req.user.id);
  if (!candidateProfileId) {
    return res.status(404).json({ status: 'error', message: 'Candidate profile not found' });
  }

  const [rows] = await pool.query(
    `SELECT * FROM applications WHERE id = ? AND candidate_profile_id = ?`,
    [req.params.id, candidateProfileId]
  );
  const application = rows[0];
  if (!application) {
    return res.status(404).json({ status: 'error', message: 'Application not found' });
  }

  const nonWithdrawableStatuses = ['offered', 'hired', 'rejected', 'withdrawn'];
  if (nonWithdrawableStatuses.includes(application.status)) {
    return res.status(400).json({ status: 'error', message: `Cannot withdraw an application with status '${application.status}'` });
  }

  await pool.query(`UPDATE applications SET status = 'withdrawn' WHERE id = ?`, [req.params.id]);
  res.json({ status: 'ok', message: 'Application withdrawn' });
}

// GET /api/v1/applications (Recruiter, HR Director, Super Admin)
async function listAllApplications(req, res) {
  const { vacancy_id, status, candidate_profile_id } = req.query;
  const conditions = [];
  const params = [];

  if (vacancy_id) { conditions.push('a.vacancy_id = ?'); params.push(vacancy_id); }
  if (status) { conditions.push('a.status = ?'); params.push(status); }
  if (candidate_profile_id) { conditions.push('a.candidate_profile_id = ?'); params.push(candidate_profile_id); }

  const whereClause = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';

  const [rows] = await pool.query(
    `SELECT a.*, v.title AS vacancy_title, cp.first_name, cp.last_name
     FROM applications a
     JOIN vacancies v ON v.id = a.vacancy_id
     JOIN candidate_profiles cp ON cp.id = a.candidate_profile_id
     ${whereClause}
     ORDER BY a.applied_at DESC`,
    params
  );

  res.json({ status: 'ok', applications: rows });
}

// GET /api/v1/applications/:id (Recruiter, HR Director, Super Admin)
async function getApplicationById(req, res) {
  const [rows] = await pool.query(
    `SELECT a.*, v.title AS vacancy_title, cp.first_name, cp.last_name, cp.resume_path, cp.cover_letter_path
     FROM applications a
     JOIN vacancies v ON v.id = a.vacancy_id
     JOIN candidate_profiles cp ON cp.id = a.candidate_profile_id
     WHERE a.id = ?`,
    [req.params.id]
  );
  if (rows.length === 0) {
    return res.status(404).json({ status: 'error', message: 'Application not found' });
  }
  res.json({ status: 'ok', application: rows[0] });
}

// PUT /api/v1/applications/:id/status (Recruiter, HR Director, Super Admin)
async function updateApplicationStatus(req, res) {
  const { status } = req.body;
  const validStatuses = ['submitted', 'under_review', 'shortlisted', 'interview', 'offered', 'rejected', 'withdrawn', 'hired'];
  if (!validStatuses.includes(status)) {
    return res.status(400).json({ status: 'error', message: 'Invalid status' });
  }

  const [result] = await pool.query(`UPDATE applications SET status = ? WHERE id = ?`, [status, req.params.id]);
  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Application not found' });
  }
  res.json({ status: 'ok', message: 'Application status updated' });
}

module.exports = {
  applyToVacancy,
  listMyApplications,
  withdrawApplication,
  listAllApplications,
  getApplicationById,
  updateApplicationStatus
};

const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit } = require('../utils/format');

function mapScore(row) {
  return {
    id: row.id,
    applicationId: row.application_id,
    scorerUserId: row.scorer_user_id,
    scorerName: row.scorer_name,
    scorerEmail: row.scorer_email,
    score: row.score != null ? parseFloat(row.score) : null,
    comment: row.comment,
    updatedAt: row.updated_at
  };
}

// GET /api/candidate-scores?jobId=X — every scored (and unscored) application
// for a job, each with the full list of panelist scores/comments (visible to
// admins — see canShortlist gate) and the computed average. Powers the panel
// scoring table in one call instead of one request per candidate.
router.get('/', verifyToken, requirePerm('canShortlist'), asyncHandler(async (req, res) => {
  const { jobId, status } = req.query;
  if (!jobId) return fail(res, 'jobId is required');

  const conditions = ['job_id = ?'];
  const params = [jobId];
  if (status) { conditions.push('status = ?'); params.push(status); }

  const [apps] = await pool.query(
    `SELECT id, candidate_name, candidate_email, status FROM applications WHERE ${conditions.join(' AND ')}`,
    params
  );
  if (apps.length === 0) return okList(res, []);

  const [scoreRows] = await pool.query(
    `SELECT cs.*, CONCAT(u.first_name, ' ', u.last_name) AS scorer_name, u.email AS scorer_email
     FROM candidate_scores cs
     JOIN users u ON u.id = cs.scorer_user_id
     WHERE cs.application_id IN (?)`,
    [apps.map((a) => a.id)]
  );

  const byApp = new Map(apps.map((a) => [a.id, { applicationId: a.id, candidateName: a.candidate_name, candidateEmail: a.candidate_email, status: a.status, scores: [] }]));
  scoreRows.forEach((r) => byApp.get(r.application_id)?.scores.push(mapScore(r)));

  const result = Array.from(byApp.values()).map((a) => ({
    ...a,
    average: a.scores.length ? Math.round((a.scores.reduce((s, x) => s + (x.score ?? 0), 0) / a.scores.length) * 100) / 100 : null,
  }));
  return okList(res, result);
}));

// PUT /api/candidate-scores/:applicationId — upsert the CURRENT admin's own
// score+comment. Each panelist has exactly one row per application (unique on
// application_id+scorer_user_id) so multiple admins scoring the same
// candidate never overwrite each other, unlike the Phase 2b `assessments`
// table's single-score-total design.
router.put('/:applicationId', verifyToken, requirePerm('canShortlist'), asyncHandler(async (req, res) => {
  const { score, comment } = req.body;
  if (score == null || Number.isNaN(Number(score))) return fail(res, 'A numeric score is required');

  const [apps] = await pool.query('SELECT candidate_name FROM applications WHERE id = ?', [req.params.applicationId]);
  if (apps.length === 0) return fail(res, 'Application not found', 404);

  await pool.query(
    `INSERT INTO candidate_scores (application_id, scorer_user_id, score, comment)
     VALUES (?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE score = VALUES(score), comment = VALUES(comment), updated_at = NOW()`,
    [req.params.applicationId, req.user.id, score, comment || null]
  );
  await logAudit(pool, req, 'Scored candidate', `${apps[0].candidate_name} — ${score}`);

  const [rows] = await pool.query(
    `SELECT cs.*, CONCAT(u.first_name, ' ', u.last_name) AS scorer_name, u.email AS scorer_email
     FROM candidate_scores cs JOIN users u ON u.id = cs.scorer_user_id
     WHERE cs.application_id = ? AND cs.scorer_user_id = ?`,
    [req.params.applicationId, req.user.id]
  );
  return ok(res, mapScore(rows[0]));
}));

module.exports = router;

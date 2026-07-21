const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit } = require('../utils/format');
const mailer = require('../utils/mailer');

const STATUSES = ['pending', 'contacted', 'verified', 'could_not_reach', 'declined_to_confirm'];

function mapCheck(row) {
  return {
    id: row.id,
    applicationId: row.application_id,
    refereeIndex: row.referee_index,
    refereeName: row.referee_name,
    refereeEmail: row.referee_email,
    refereePhone: row.referee_phone,
    status: row.status,
    notes: row.notes,
    contactedAt: row.contacted_at
  };
}

// GET /api/background-checks — every background check across every
// candidate, for the Candidate Background Check report.
router.get('/', verifyToken, requirePerm('canExport'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(`
    SELECT bc.*, ap.candidate_name, ap.title AS job_title, ap.dept
    FROM background_checks bc
    JOIN applications ap ON ap.id = bc.application_id
    ORDER BY bc.updated_at DESC
  `);
  return okList(res, rows.map((r) => ({
    ...mapCheck(r), candidateName: r.candidate_name, jobTitle: r.job_title, dept: r.dept
  })));
}));

// GET /api/background-checks/:applicationId
router.get('/:applicationId', verifyToken, requirePerm('canViewApplications'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM background_checks WHERE application_id = ? ORDER BY referee_index', [req.params.applicationId]
  );
  return okList(res, rows.map(mapCheck));
}));

// POST /api/background-checks/:applicationId/init
// Snapshots the candidate's 2 CV referees into background_checks rows —
// snapshotted (not read live from the CV) so the contact details used for
// the check don't silently change if the candidate edits their CV later.
router.post('/:applicationId/init', verifyToken, requirePerm('canManageBackgroundChecks'), asyncHandler(async (req, res) => {
  const { applicationId } = req.params;
  const [appRows] = await pool.query('SELECT * FROM applications WHERE id = ?', [applicationId]);
  if (appRows.length === 0) return fail(res, 'Application not found', 404);
  const app = appRows[0];

  const [cvRows] = await pool.query('SELECT referees FROM cv_profiles WHERE user_email = ?', [app.candidate_email]);
  const referees = cvRows.length > 0 && cvRows[0].referees ? cvRows[0].referees : [];
  if (referees.length === 0) return fail(res, 'No CV/referee data on file for this candidate');

  for (let i = 0; i < Math.min(referees.length, 2); i++) {
    const r = referees[i];
    await pool.query(
      `INSERT INTO background_checks (application_id, referee_index, referee_name, referee_email, referee_phone)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE referee_name = VALUES(referee_name), referee_email = VALUES(referee_email), referee_phone = VALUES(referee_phone)`,
      [applicationId, i, r.name || null, r.email || null, r.phone || null]
    );
  }

  await logAudit(pool, req, 'Initiated background check', `${app.candidate_name} — ${app.title}`);
  const [rows] = await pool.query('SELECT * FROM background_checks WHERE application_id = ? ORDER BY referee_index', [applicationId]);
  return okList(res, rows.map(mapCheck));
}));

// PUT /api/background-checks/:id — manual status/notes update
router.put('/:id', verifyToken, requirePerm('canManageBackgroundChecks'), asyncHandler(async (req, res) => {
  const { status, notes } = req.body;
  if (status && !STATUSES.includes(status)) return fail(res, `status must be one of: ${STATUSES.join(', ')}`);

  const [existing] = await pool.query('SELECT * FROM background_checks WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Background check record not found', 404);

  await pool.query(
    'UPDATE background_checks SET status = COALESCE(?, status), notes = COALESCE(?, notes) WHERE id = ?',
    [status || null, notes !== undefined ? notes : null, req.params.id]
  );
  await logAudit(pool, req, 'Updated background check status', `${existing[0].referee_name} — ${status || existing[0].status}`);
  const [rows] = await pool.query('SELECT * FROM background_checks WHERE id = ?', [req.params.id]);
  return ok(res, mapCheck(rows[0]));
}));

// POST /api/background-checks/:id/send-email — automated referee verification request
router.post('/:id/send-email', verifyToken, requirePerm('canManageBackgroundChecks'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query('SELECT * FROM background_checks WHERE id = ?', [req.params.id]);
  if (rows.length === 0) return fail(res, 'Background check record not found', 404);
  const check = rows[0];
  if (!check.referee_email) return fail(res, 'No email address on file for this referee');

  const [appRows] = await pool.query('SELECT * FROM applications WHERE id = ?', [check.application_id]);
  if (appRows.length === 0) return fail(res, 'Application not found', 404);
  const app = appRows[0];

  const { subject, html } = mailer.backgroundCheckRequestEmail({
    refereeName: check.referee_name,
    candidateName: app.candidate_name,
    jobTitle: app.title
  });
  await mailer.sendMail({ to: check.referee_email, subject, html });

  await pool.query(
    "UPDATE background_checks SET status = 'contacted', contacted_at = NOW(), contacted_by = ? WHERE id = ?",
    [req.user.id, req.params.id]
  );
  await logAudit(pool, req, 'Sent background check request email', `${check.referee_name} — ${app.candidate_name}`);
  const [updated] = await pool.query('SELECT * FROM background_checks WHERE id = ?', [req.params.id]);
  return ok(res, mapCheck(updated[0]));
}));

module.exports = router;

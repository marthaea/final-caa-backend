const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit, checkPerm } = require('../utils/format');

const TYPES = ['written', 'psychometric', 'interview', 'practical'];

function mapAssessment(row) {
  return {
    id: row.id,
    applicationId: row.application_id,
    type: row.type,
    scheduledAt: row.scheduled_at,
    venue: row.venue,
    score: row.score != null ? parseFloat(row.score) : null,
    passed: row.passed == null ? null : !!row.passed,
    notes: row.notes
  };
}

// GET /api/assessments — every assessment across every candidate, for the
// Assessment Schedule / Candidate Assessment reports.
router.get('/', verifyToken, requirePerm('canExport'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(`
    SELECT a.*, ap.candidate_name, ap.title AS job_title, ap.dept
    FROM assessments a
    JOIN applications ap ON ap.id = a.application_id
    ORDER BY a.scheduled_at DESC
  `);
  return okList(res, rows.map((r) => ({
    ...mapAssessment(r), candidateName: r.candidate_name, jobTitle: r.job_title, dept: r.dept
  })));
}));

// GET /api/assessments/:applicationId
router.get('/:applicationId', verifyToken, requirePerm('canViewApplications'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM assessments WHERE application_id = ? ORDER BY type', [req.params.applicationId]
  );
  return okList(res, rows.map(mapAssessment));
}));

// PUT /api/assessments/:applicationId/:type
// Schedulers (canScheduleAssessment) set date/venue; recorders (canRecordAssessment)
// set the outcome. Same upsert route — which fields land depends on what the
// caller is actually allowed to touch, checked per-request rather than
// splitting into two routes with near-identical plumbing.
router.put('/:applicationId/:type', verifyToken, asyncHandler(async (req, res) => {
  const { applicationId, type } = req.params;
  if (!TYPES.includes(type)) return fail(res, `type must be one of: ${TYPES.join(', ')}`);

  const canSchedule = await checkPerm(pool, req.user, 'canScheduleAssessment');
  const canRecord = await checkPerm(pool, req.user, 'canRecordAssessment');
  if (!canSchedule && !canRecord) return fail(res, 'Permission denied', 403);

  const [appRows] = await pool.query('SELECT * FROM applications WHERE id = ?', [applicationId]);
  if (appRows.length === 0) return fail(res, 'Application not found', 404);
  const app = appRows[0];

  const { scheduledAt, venue, score, passed, notes } = req.body;
  const fields = {};
  if (canSchedule && (scheduledAt !== undefined || venue !== undefined)) {
    fields.scheduled_at = scheduledAt || null;
    fields.venue = venue || null;
    fields.scheduled_by = req.user.id;
  }
  if (canRecord && (score !== undefined || passed !== undefined || notes !== undefined)) {
    fields.score = score != null ? score : null;
    fields.passed = passed != null ? (passed ? 1 : 0) : null;
    fields.notes = notes || null;
    fields.recorded_by = req.user.id;
  }
  if (Object.keys(fields).length === 0) {
    return fail(res, 'Nothing to update — your role can only schedule or only record assessments');
  }

  const cols = Object.keys(fields);
  const placeholders = cols.map(() => '?').join(', ');
  const updateClause = cols.map((c) => `${c} = VALUES(${c})`).join(', ');
  await pool.query(
    `INSERT INTO assessments (application_id, type, ${cols.join(', ')})
     VALUES (?, ?, ${placeholders})
     ON DUPLICATE KEY UPDATE ${updateClause}, updated_at = NOW()`,
    [applicationId, type, ...cols.map((c) => fields[c])]
  );

  // Auto-advance the application's overall status as assessments progress.
  if ('scheduled_at' in fields && app.status === 'Interview') {
    await pool.query("UPDATE applications SET status = 'Assessment Scheduled' WHERE id = ?", [applicationId]);
  }
  if ('passed' in fields) {
    const [rows] = await pool.query('SELECT scheduled_at, passed FROM assessments WHERE application_id = ?', [applicationId]);
    const allScheduledAreRecorded = rows.length > 0 && rows.every((r) => r.scheduled_at == null || r.passed !== null);
    if (allScheduledAreRecorded && (app.status === 'Assessment Scheduled' || app.status === 'Interview')) {
      await pool.query("UPDATE applications SET status = 'Assessment Complete' WHERE id = ?", [applicationId]);
    }
  }

  await logAudit(pool, req, `Updated ${type} assessment`, `${app.candidate_name} — ${app.title}`);
  const [rows] = await pool.query('SELECT * FROM assessments WHERE application_id = ? AND type = ?', [applicationId, type]);
  return ok(res, mapAssessment(rows[0]));
}));

module.exports = router;

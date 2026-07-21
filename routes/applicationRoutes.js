const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, formatDate, logAudit, checkPerm } = require('../utils/format');
const validate = require('../middleware/validate');
const { submitApplicationRules, statusUpdateRules, bulkStatusRules } = require('../validators/applicationValidators');
const mailer = require('../utils/mailer');

let _settingsCache = null;
let _settingsCacheTime = 0;
async function getSettings() {
  const now = Date.now();
  if (_settingsCache && now - _settingsCacheTime < 60000) return _settingsCache;
  const [rows] = await pool.query('SELECT * FROM settings LIMIT 1');
  _settingsCache = rows[0];
  _settingsCacheTime = now;
  return _settingsCache;
}

function mapApp(row) {
  return {
    id: row.id,
    jobId: row.job_id,
    abbr: row.abbr,
    title: row.title,
    dept: row.dept,
    date: row.date || formatDate(row.applied_at),
    status: row.status,
    completion: row.completion,
    candidateName: row.candidate_name,
    candidateEmail: row.candidate_email,
    cgpa: row.cgpa != null ? parseFloat(row.cgpa) : null,
    university: row.university || null,
    screeningAnswers: row.screening_answers || null,
    deploymentStation: row.deployment_station || null,
    deploymentDate: row.deployment_date
      ? (row.deployment_date instanceof Date ? row.deployment_date.toISOString().slice(0, 10) : String(row.deployment_date))
      : null
  };
}

function screeningAnswerPasses(question, answer) {
  if (!question.kind) return true;
  if (question.kind === 'yesno') {
    const qualifying = question.qualifyingAnswer || 'Yes';
    return answer === qualifying;
  }
  if (question.kind === 'number') {
    const num = parseFloat(answer);
    if (isNaN(num)) return false;
    if (question.min != null && num < question.min) return false;
    if (question.max != null && num > question.max) return false;
    return true;
  }
  return true;
}

// GET /api/applications
router.get('/', verifyToken, asyncHandler(async (req, res) => {
  if (req.user.accountType === 'admin') {
    const allowed = await checkPerm(pool, req.user, 'canViewApplications');
    if (!allowed) return res.status(403).json({ success: false, error: 'Permission denied' });

    const { jobId, status, fromDate, toDate, email } = req.query;
    const conditions = [];
    const params = [];

    if (jobId)    { conditions.push('job_id = ?');             params.push(jobId); }
    if (status)   { conditions.push('status = ?');             params.push(status); }
    if (fromDate) { conditions.push('applied_at >= ?');        params.push(fromDate); }
    if (toDate)   { conditions.push('applied_at <= ?');        params.push(toDate); }
    if (email)    { conditions.push('candidate_email LIKE ?'); params.push(`%${email}%`); }

    const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
    const limit = parseInt(req.query.limit) || 500;
    const offset = parseInt(req.query.offset) || 0;

    const [rows] = await pool.query(
      `SELECT * FROM applications ${where} ORDER BY applied_at DESC LIMIT ? OFFSET ?`,
      [...params, limit, offset]
    );
    return okList(res, rows.map(mapApp));
  } else {
    const [rows] = await pool.query(
      'SELECT * FROM applications WHERE candidate_email = ? ORDER BY applied_at DESC',
      [req.user.email]
    );
    return okList(res, rows.map(mapApp));
  }
}));

// GET /api/applications/export  (must be defined before /:id to avoid routing conflict)
router.get('/export', verifyToken, requirePerm('canViewApplications'), asyncHandler(async (req, res) => {
  const { jobId, status, fromDate, toDate, email } = req.query;
  const conditions = [];
  const params = [];
  if (jobId)    { conditions.push('job_id = ?');             params.push(jobId); }
  if (status)   { conditions.push('status = ?');             params.push(status); }
  if (fromDate) { conditions.push('applied_at >= ?');        params.push(fromDate); }
  if (toDate)   { conditions.push('applied_at <= ?');        params.push(toDate); }
  if (email)    { conditions.push('candidate_email LIKE ?'); params.push(`%${email}%`); }
  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';

  const [rows] = await pool.query(
    `SELECT id, candidate_name, candidate_email, abbr, title, dept,
            date, status, completion, cgpa, university, applied_at
     FROM applications ${where}
     ORDER BY applied_at DESC
     LIMIT 5000`,
    params
  );

  const headers = ['ID','Name','Email','Abbr','Job Title','Department','Date','Status','Completion%','CGPA','University'];
  const escape  = v => v == null ? '' : `"${String(v).replace(/"/g, '""')}"`;
  const lines   = [
    headers.join(','),
    ...rows.map(r => [
      r.id, escape(r.candidate_name), escape(r.candidate_email),
      escape(r.abbr), escape(r.title), escape(r.dept),
      escape(r.date), escape(r.status), r.completion,
      r.cgpa != null ? r.cgpa : '', escape(r.university)
    ].join(','))
  ];

  res.setHeader('Content-Type', 'text/csv');
  res.setHeader('Content-Disposition', `attachment; filename="applications_${Date.now()}.csv"`);
  return res.send(lines.join('\r\n'));
}));

// POST /api/applications
router.post('/', verifyToken, submitApplicationRules, validate, asyncHandler(async (req, res) => {
  const { jobId, cgpa, university, screeningAnswers, completion } = req.body;
  if (!jobId) return fail(res, 'jobId is required');

  // Verify job exists and is not expired
  const [jobs] = await pool.query(
    'SELECT * FROM jobs WHERE id = ? AND closes_at >= CURDATE()', [jobId]
  );
  if (jobs.length === 0) {
    const [anyJob] = await pool.query('SELECT id FROM jobs WHERE id = ?', [jobId]);
    return anyJob.length === 0
      ? fail(res, 'Job not found', 404)
      : fail(res, 'This vacancy is no longer accepting applications');
  }
  const job = jobs[0];

  // Prevent duplicate applications
  const [dup] = await pool.query(
    'SELECT id FROM applications WHERE job_id = ? AND candidate_email = ? LIMIT 1',
    [jobId, req.user.email]
  );
  if (dup.length > 0) {
    return fail(res, 'You have already applied for this position', 409);
  }

  // Check application limit
  const settings = await getSettings();
  if (settings.max_applications_per_candidate > 0) {
    const [countRows] = await pool.query(
      `SELECT COUNT(*) AS cnt FROM applications
       WHERE candidate_email = ? AND status != 'Declined'`,
      [req.user.email]
    );
    if (countRows[0].cnt >= settings.max_applications_per_candidate) {
      return fail(res,
        `Application limit reached. You may not have more than ${settings.max_applications_per_candidate} active applications.`
      );
    }
  }

  // Auto-screening
  let status = 'Pending';
  const [criteriaRows] = await pool.query(
    'SELECT * FROM criteria WHERE job_id = ? LIMIT 1', [jobId]
  );
  if (criteriaRows.length > 0) {
    const c = criteriaRows[0];

    // CGPA check
    if (c.min_cgpa != null && cgpa != null && parseFloat(cgpa) < parseFloat(c.min_cgpa)) {
      status = 'Declined';
    }

    // Disqualifying universities
    if (status !== 'Declined' && c.disqualifying_universities && university) {
      const disq = Array.isArray(c.disqualifying_universities)
        ? c.disqualifying_universities
        : JSON.parse(c.disqualifying_universities);
      if (disq.some(u => u.toLowerCase() === university.toLowerCase())) {
        status = 'Declined';
      }
    }

    // Screening questions (structured kinds only)
    if (status !== 'Declined' && c.screening_questions && screeningAnswers) {
      const questions = Array.isArray(c.screening_questions)
        ? c.screening_questions
        : JSON.parse(c.screening_questions);
      for (const q of questions) {
        if (q.kind) {
          const answer = screeningAnswers[q.id];
          if (!screeningAnswerPasses(q, answer)) {
            status = 'Declined';
            break;
          }
        }
      }
    }
  }

  const candidateName = `${req.user.firstName} ${req.user.lastName}`;
  const dateStr = formatDate(new Date());

  const [result] = await pool.query(
    `INSERT INTO applications
       (job_id, candidate_email, candidate_name, abbr, title, dept, date,
        status, completion, cgpa, university, screening_answers)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      jobId, req.user.email, candidateName,
      job.abbr, job.title, job.dept, dateStr,
      status, completion || 0,
      cgpa != null ? cgpa : null,
      university || null,
      screeningAnswers ? JSON.stringify(screeningAnswers) : null
    ]
  );

  const [apps] = await pool.query('SELECT * FROM applications WHERE id = ?', [result.insertId]);
  return ok(res, mapApp(apps[0]), 201);
}));

// PUT /api/applications/bulk-status  (defined before /:id/status to avoid routing conflicts)
router.put('/bulk-status', verifyToken, requirePerm('canShortlist'), bulkStatusRules, validate, asyncHandler(async (req, res) => {
  const { updates } = req.body;
  if (!Array.isArray(updates) || updates.length === 0) {
    return fail(res, 'updates array is required and must not be empty');
  }
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    for (const { id, status } of updates) {
      await conn.query('UPDATE applications SET status = ? WHERE id = ?', [status, id]);
    }
    await conn.commit();
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
  await logAudit(pool, req, `Bulk status update (${updates.length} applications)`);
  return ok(res, { updated: updates.length });
}));

// PUT /api/applications/:id/status
router.put('/:id/status', verifyToken, requirePerm('canShortlist'), statusUpdateRules, validate, asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT * FROM applications WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Application not found', 404);
  const app = existing[0];

  const { status, notifyEmail, notifyMessage } = req.body;
  await pool.query(
    'UPDATE applications SET status = ?, updated_at = NOW() WHERE id = ?',
    [status, req.params.id]
  );

  if (notifyEmail && notifyMessage) {
    const typeMap = {
      Shortlisted: 'shortlisted', Declined: 'declined',
      Interview: 'interview', Offered: 'offered'
    };
    const notifType = typeMap[status] || 'info';
    await pool.query(
      `INSERT INTO notifications (recipient_email, title, message, type)
       VALUES (?, ?, ?, ?)`,
      [notifyEmail, `Application status: ${status}`, notifyMessage, notifType]
    );
    // Fire-and-forget actual email — don't await so a mail failure never blocks the response
    const { subject, html } = mailer.applicationStatusEmail({
      candidateName: app.candidate_name,
      jobTitle:      app.title,
      status,
      message:       notifyMessage
    });
    mailer.sendMail({ to: notifyEmail, subject, html }).catch(err =>
      console.error('[mailer] status-update email failed:', err.message)
    );
  }

  // Automatic intern-acceptance email — always fires when an intern (identified by
  // having a CGPA on file) is offered a position, regardless of whether the admin
  // above wrote a custom notify message. Interns need this to get an airport pass.
  if (status === 'Offered' && app.cgpa != null) {
    const [jobRows] = await pool.query('SELECT location FROM jobs WHERE id = ?', [app.job_id]);
    const { subject: internSubject, html: internHtml } = mailer.internAcceptanceEmail({
      candidateName: app.candidate_name,
      jobTitle:      app.title,
      location:      jobRows[0]?.location || null
    });
    mailer.sendMail({ to: app.candidate_email, subject: internSubject, html: internHtml }).catch(err =>
      console.error('[mailer] intern acceptance email failed:', err.message)
    );
  }

  await logAudit(pool, req,
    `Updated application status to ${status}`,
    `${app.candidate_name} (${app.abbr})`
  );

  const [updated] = await pool.query('SELECT * FROM applications WHERE id = ?', [req.params.id]);
  return ok(res, mapApp(updated[0]));
}));

// PUT /api/applications/:id/deployment — station/reporting date once an offer is accepted
router.put('/:id/deployment', verifyToken, requirePerm('canManageBackgroundChecks'), asyncHandler(async (req, res) => {
  const { deploymentStation, deploymentDate } = req.body;
  const [existing] = await pool.query('SELECT * FROM applications WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Application not found', 404);

  await pool.query(
    'UPDATE applications SET deployment_station = ?, deployment_date = ? WHERE id = ?',
    [deploymentStation || null, deploymentDate || null, req.params.id]
  );
  await logAudit(pool, req, 'Recorded candidate deployment', `${existing[0].candidate_name} (${existing[0].abbr})`);
  const [updated] = await pool.query('SELECT * FROM applications WHERE id = ?', [req.params.id]);
  return ok(res, mapApp(updated[0]));
}));

// DELETE /api/applications/:id
router.delete('/:id', verifyToken, asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM applications WHERE id = ? AND candidate_email = ?',
    [req.params.id, req.user.email]
  );
  if (rows.length === 0) return fail(res, 'Application not found', 404);
  const app = rows[0];

  const nonWithdrawable = ['Shortlisted', 'Interview', 'Offered'];
  if (nonWithdrawable.includes(app.status)) {
    return fail(res, 'Cannot withdraw an application at this stage');
  }

  await pool.query('DELETE FROM applications WHERE id = ?', [req.params.id]);
  return ok(res, { message: 'Withdrawn' });
}));

module.exports = router;

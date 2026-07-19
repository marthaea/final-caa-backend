const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requireRole, requirePerm } = require('../middleware/rbac');
const { ok, fail } = require('../utils/format');
const mailer = require('../utils/mailer');

function mapEmail(row) {
  return {
    id: row.id,
    to: row.to_email,
    candidateName: row.candidate_name,
    subject: row.subject,
    body: row.body,
    sentAt: new Date(row.sent_at).toISOString(),
    trigger: row.trigger_event,
    jobTitle: row.job_title
  };
}

// GET /api/emails
router.get('/', verifyToken, requirePerm('canViewApplications'), asyncHandler(async (req, res) => {
  const { search, triggerEvent } = req.query;
  const limit = Math.min(parseInt(req.query.limit) || 500, 2000);
  const conditions = [];
  const params = [];

  if (search) {
    conditions.push('(to_email LIKE ? OR candidate_name LIKE ? OR subject LIKE ?)');
    const like = `%${search}%`;
    params.push(like, like, like);
  }
  if (triggerEvent) {
    conditions.push('trigger_event = ?');
    params.push(triggerEvent);
  }

  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const [rows] = await pool.query(
    `SELECT * FROM sent_emails ${where} ORDER BY sent_at DESC LIMIT ?`,
    [...params, limit]
  );
  return res.json({ success: true, data: rows.map(mapEmail), total: rows.length });
}));

// POST /api/emails
router.post('/', verifyToken, requirePerm('canSendNotifications'), asyncHandler(async (req, res) => {
  const { to, candidateName, subject, body, trigger, jobTitle } = req.body;
  if (!to || !candidateName || !subject || !body || !trigger || !jobTitle) {
    return fail(res, 'Missing required fields');
  }
  const [result] = await pool.query(
    `INSERT INTO sent_emails (to_email, candidate_name, subject, body, trigger_event, job_title)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [to, candidateName, subject, body, trigger, jobTitle]
  );

  // Actually deliver the email — non-blocking
  const { html } = mailer.bulkEmail({ candidateName, subject, body });
  mailer.sendMail({ to, subject, html }).catch(err =>
    console.error('[mailer] single email failed:', err.message)
  );

  const [rows] = await pool.query('SELECT * FROM sent_emails WHERE id = ?', [result.insertId]);
  return ok(res, mapEmail(rows[0]), 201);
}));

// POST /api/emails/bulk
router.post('/bulk', verifyToken, requirePerm('canSendNotifications'), asyncHandler(async (req, res) => {
  const { emails } = req.body;
  if (!Array.isArray(emails) || emails.length === 0) {
    return fail(res, 'emails array is required and must not be empty');
  }

  const values = emails.map(e => [
    e.to, e.candidateName, e.subject, e.body, e.trigger, e.jobTitle
  ]);

  await pool.query(
    `INSERT INTO sent_emails (to_email, candidate_name, subject, body, trigger_event, job_title)
     VALUES ?`,
    [values]
  );

  // Deliver each email — non-blocking
  for (const e of emails) {
    const { html } = mailer.bulkEmail({ candidateName: e.candidateName, subject: e.subject, body: e.body });
    mailer.sendMail({ to: e.to, subject: e.subject, html }).catch(err =>
      console.error(`[mailer] bulk email to ${e.to} failed:`, err.message)
    );
  }

  return ok(res, { inserted: emails.length }, 201);
}));

// DELETE /api/emails
router.delete('/', verifyToken, requireRole('super'), asyncHandler(async (req, res) => {
  await pool.query('DELETE FROM sent_emails');
  return ok(res, { message: 'Email log cleared' });
}));

module.exports = router;

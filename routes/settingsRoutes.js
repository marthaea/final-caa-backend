const router = require('express').Router();
const pool = require('../config/db');
const { verifyToken } = require('../middleware/auth');
const { requireRole, requirePerm } = require('../middleware/rbac');
const { ok, logAudit } = require('../utils/format');

function mapSettings(row) {
  return {
    orgName: row.org_name,
    emailSenderName: row.email_sender_name,
    minAgeThreshold: row.min_age_threshold,
    allowExternalInternalJobs: !!row.allow_external_internal_jobs,
    sessionTimeoutMinutes: row.session_timeout_minutes,
    closingSoonDays: row.closing_soon_days,
    maxApplicationsPerCandidate: row.max_applications_per_candidate,
    notifTemplates: {
      shortlist: row.notif_template_shortlist,
      decline: row.notif_template_decline,
      interview: row.notif_template_interview,
      offer: row.notif_template_offer
    }
  };
}

// GET /api/settings
router.get('/', verifyToken, requirePerm('canManageSettings'), async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT * FROM settings LIMIT 1');
    return ok(res, mapSettings(rows[0]));
  } catch (e) {
    console.error('GET /settings:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// PUT /api/settings
router.put('/', verifyToken, requireRole('super'), async (req, res) => {
  try {
    const {
      orgName, emailSenderName, minAgeThreshold, allowExternalInternalJobs,
      sessionTimeoutMinutes, closingSoonDays, maxApplicationsPerCandidate,
      notifTemplates
    } = req.body;

    await pool.query(
      `UPDATE settings SET
        org_name                       = COALESCE(?, org_name),
        email_sender_name              = COALESCE(?, email_sender_name),
        min_age_threshold              = COALESCE(?, min_age_threshold),
        allow_external_internal_jobs   = COALESCE(?, allow_external_internal_jobs),
        session_timeout_minutes        = COALESCE(?, session_timeout_minutes),
        closing_soon_days              = COALESCE(?, closing_soon_days),
        max_applications_per_candidate = COALESCE(?, max_applications_per_candidate),
        notif_template_shortlist       = COALESCE(?, notif_template_shortlist),
        notif_template_decline         = COALESCE(?, notif_template_decline),
        notif_template_interview       = COALESCE(?, notif_template_interview),
        notif_template_offer           = COALESCE(?, notif_template_offer)
       WHERE id = 1`,
      [
        orgName || null,
        emailSenderName || null,
        minAgeThreshold != null ? minAgeThreshold : null,
        allowExternalInternalJobs != null ? (allowExternalInternalJobs ? 1 : 0) : null,
        sessionTimeoutMinutes != null ? sessionTimeoutMinutes : null,
        closingSoonDays != null ? closingSoonDays : null,
        maxApplicationsPerCandidate != null ? maxApplicationsPerCandidate : null,
        notifTemplates?.shortlist  || null,
        notifTemplates?.decline    || null,
        notifTemplates?.interview  || null,
        notifTemplates?.offer      || null
      ]
    );

    await logAudit(pool, req, 'Updated portal settings');
    const [rows] = await pool.query('SELECT * FROM settings LIMIT 1');
    return ok(res, mapSettings(rows[0]));
  } catch (e) {
    console.error('PUT /settings:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

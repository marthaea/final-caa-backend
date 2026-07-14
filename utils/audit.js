// Enhanced audit utility. Routes can call audit.fromReq() for the common
// case, or audit.log() directly when req is not available.

const ACTIONS = {
  USER_REGISTERED:    'User registered',
  USER_LOGGED_IN:     'User logged in',
  JOB_CREATED:        'Created job listing',
  JOB_UPDATED:        'Updated job listing',
  JOB_DELETED:        'Deleted job listing',
  APP_STATUS_CHANGED: 'Updated application status',
  APP_BULK_UPDATED:   'Bulk status update',
  SETTINGS_UPDATED:   'Updated portal settings',
  PERMS_UPDATED:      'Updated permissions',
  CRITERIA_UPDATED:   'Updated criteria',
  EMAIL_LOG_CLEARED:  'Cleared email log',
  FILE_UPLOADED:      'File uploaded',
};

async function log(pool, { actor, role, action, target = null }) {
  try {
    await pool.query(
      'INSERT INTO audit_log (actor, role, action, target) VALUES (?, ?, ?, ?)',
      [actor, role, action, target]
    );
  } catch (e) {
    console.error('[audit] DB write failed:', e.message);
  }
}

function fromReq(pool, req, action, target = null) {
  const actor = req.user
    ? `${req.user.firstName} ${req.user.lastName}`
    : 'Anonymous';
  const role = req.user?.adminRole || req.user?.accountType || 'unknown';
  return log(pool, { actor, role, action, target });
}

module.exports = { log, fromReq, ACTIONS };

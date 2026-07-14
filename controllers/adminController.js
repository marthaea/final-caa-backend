// controllers/adminController.js
const pool = require('../config/db');

// GET /api/v1/admin/audit-logs?page=1&limit=50&user_id=&action=
async function listAuditLogs(req, res) {
  const page = parseInt(req.query.page, 10) || 1;
  const limit = parseInt(req.query.limit, 10) || 50;
  const offset = (page - 1) * limit;

  const conditions = [];
  const params = [];
  if (req.query.user_id) { conditions.push('user_id = ?'); params.push(req.query.user_id); }
  if (req.query.action) { conditions.push('action = ?'); params.push(req.query.action); }

  const whereClause = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';

  const [rows] = await pool.query(
    `SELECT * FROM audit_logs ${whereClause} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
    [...params, limit, offset]
  );
  const [[{ total }]] = await pool.query(
    `SELECT COUNT(*) AS total FROM audit_logs ${whereClause}`,
    params
  );

  res.json({ status: 'ok', logs: rows, pagination: { page, limit, total } });
}

// GET /api/v1/admin/settings
async function getSettings(req, res) {
  const [rows] = await pool.query(`SELECT * FROM settings`);
  res.json({ status: 'ok', settings: rows });
}

// PUT /api/v1/admin/settings/:key
async function updateSetting(req, res) {
  const { value } = req.body;
  await pool.query(
    `INSERT INTO settings (\`key\`, \`value\`) VALUES (?, ?)
     ON DUPLICATE KEY UPDATE \`value\` = VALUES(\`value\`)`,
    [req.params.key, value]
  );
  res.json({ status: 'ok', message: 'Setting updated' });
}

// GET /api/v1/admin/permissions
// Roles are a fixed ENUM in this schema, so this returns the static
// role -> capability map rather than a dynamic permissions table.
async function getPermissions(req, res) {
  const permissions = {
    external_candidate: ['view_public_vacancies', 'manage_own_profile', 'apply_to_vacancy', 'view_own_applications'],
    internal_candidate: ['view_all_vacancies', 'manage_own_profile', 'apply_to_vacancy', 'view_own_applications'],
    recruiter: ['view_all_applications', 'change_application_status', 'shortlist_applications', 'schedule_interviews'],
    hr_director: ['manage_vacancies', 'manage_applications', 'extend_offers', 'view_reports'],
    super_admin: ['full_access', 'manage_users', 'manage_roles', 'view_audit_logs', 'manage_settings']
  };
  res.json({ status: 'ok', permissions });
}

// PUT /api/v1/admin/permissions/:role
// Placeholder: with a fixed ENUM role model there's no per-role table to
// persist to. If granular permissions are needed later, add a
// role_permissions table and update this to write there.
async function updateRolePermissions(req, res) {
  res.status(501).json({
    status: 'error',
    message: 'Granular role permission editing requires a role_permissions table (not in current schema)'
  });
}

module.exports = {
  listAuditLogs,
  getSettings,
  updateSetting,
  getPermissions,
  updateRolePermissions
};

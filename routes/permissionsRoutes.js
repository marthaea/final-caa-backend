const router = require('express').Router();
const pool = require('../config/db');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, logAudit } = require('../utils/format');

function mapOverride(row) {
  return {
    email: row.email,
    role: row.role,
    canViewApplications:  !!row.can_view_applications,
    canShortlist:         !!row.can_shortlist,
    canScreenInterns:     !!row.can_screen_interns,
    canSendNotifications: !!row.can_send_notifications,
    canManageJobs:        !!row.can_manage_jobs,
    canManageCriteria:    !!row.can_manage_criteria,
    canViewStaff:         !!row.can_view_staff,
    canExport:            !!row.can_export,
    canViewAudit:         !!row.can_view_audit,
    canManageSettings:    !!row.can_manage_settings,
    canGrantPermissions:  !!row.can_grant_permissions
  };
}

// GET /api/permissions
router.get('/', verifyToken, requirePerm('canGrantPermissions'), async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT * FROM permission_overrides ORDER BY email');
    return okList(res, rows.map(mapOverride));
  } catch (e) {
    console.error('GET /permissions:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// PUT /api/permissions
router.put('/', verifyToken, requirePerm('canGrantPermissions'), async (req, res) => {
  try {
    const {
      email, role,
      canViewApplications, canShortlist, canScreenInterns,
      canSendNotifications, canManageJobs, canManageCriteria,
      canViewStaff, canExport, canViewAudit,
      canManageSettings, canGrantPermissions
    } = req.body;

    await pool.query(
      `INSERT INTO permission_overrides
         (email, role, can_view_applications, can_shortlist, can_screen_interns,
          can_send_notifications, can_manage_jobs, can_manage_criteria,
          can_view_staff, can_export, can_view_audit, can_manage_settings, can_grant_permissions)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         role                    = VALUES(role),
         can_view_applications   = VALUES(can_view_applications),
         can_shortlist           = VALUES(can_shortlist),
         can_screen_interns      = VALUES(can_screen_interns),
         can_send_notifications  = VALUES(can_send_notifications),
         can_manage_jobs         = VALUES(can_manage_jobs),
         can_manage_criteria     = VALUES(can_manage_criteria),
         can_view_staff          = VALUES(can_view_staff),
         can_export              = VALUES(can_export),
         can_view_audit          = VALUES(can_view_audit),
         can_manage_settings     = VALUES(can_manage_settings),
         can_grant_permissions   = VALUES(can_grant_permissions),
         updated_at              = NOW()`,
      [
        email, role,
        canViewApplications  ? 1 : 0,
        canShortlist         ? 1 : 0,
        canScreenInterns     ? 1 : 0,
        canSendNotifications ? 1 : 0,
        canManageJobs        ? 1 : 0,
        canManageCriteria    ? 1 : 0,
        canViewStaff         ? 1 : 0,
        canExport            ? 1 : 0,
        canViewAudit         ? 1 : 0,
        canManageSettings    ? 1 : 0,
        canGrantPermissions  ? 1 : 0
      ]
    );

    await logAudit(pool, req, 'Updated permissions', `${email} (${role})`);
    const [rows] = await pool.query(
      'SELECT * FROM permission_overrides WHERE email = ? LIMIT 1', [email]
    );
    return ok(res, mapOverride(rows[0]));
  } catch (e) {
    console.error('PUT /permissions:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

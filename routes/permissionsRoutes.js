const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, logAudit } = require('../utils/format');
const { ROLE_DEFAULTS, ADMIN_ROLES } = require('../config/constants');

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
    canGrantPermissions:  !!row.can_grant_permissions,
    canReviewJob:         !!row.can_review_job,
    canApproveJob:        !!row.can_approve_job,
    canManageDepartments: !!row.can_manage_departments,
    canManageAdmins:      !!row.can_manage_admins,
    canAssignRights:      !!row.can_assign_rights
  };
}

// GET /api/permissions/roles/defaults
// The single source of truth for role→permission defaults — the frontend
// fetches this instead of hardcoding its own copy (previously 3 separate
// hardcoded copies existed across the two repos and had to be kept in sync
// by hand).
router.get('/roles/defaults', verifyToken, asyncHandler(async (req, res) => {
  return ok(res, { roles: ADMIN_ROLES, defaults: ROLE_DEFAULTS });
}));

// GET /api/permissions
router.get('/', verifyToken, requirePerm('canGrantPermissions'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query('SELECT * FROM permission_overrides ORDER BY email');
  return okList(res, rows.map(mapOverride));
}));

// PUT /api/permissions
router.put('/', verifyToken, requirePerm('canGrantPermissions'), asyncHandler(async (req, res) => {
  const {
    email, role,
    canViewApplications, canShortlist, canScreenInterns,
    canSendNotifications, canManageJobs, canManageCriteria,
    canViewStaff, canExport, canViewAudit,
    canManageSettings, canGrantPermissions,
    canReviewJob, canApproveJob, canManageDepartments,
    canManageAdmins, canAssignRights
  } = req.body;

  await pool.query(
    `INSERT INTO permission_overrides
       (email, role, can_view_applications, can_shortlist, can_screen_interns,
        can_send_notifications, can_manage_jobs, can_manage_criteria,
        can_view_staff, can_export, can_view_audit, can_manage_settings, can_grant_permissions,
        can_review_job, can_approve_job, can_manage_departments, can_manage_admins, can_assign_rights)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
       can_review_job          = VALUES(can_review_job),
       can_approve_job         = VALUES(can_approve_job),
       can_manage_departments  = VALUES(can_manage_departments),
       can_manage_admins       = VALUES(can_manage_admins),
       can_assign_rights       = VALUES(can_assign_rights),
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
      canGrantPermissions  ? 1 : 0,
      canReviewJob         ? 1 : 0,
      canApproveJob        ? 1 : 0,
      canManageDepartments ? 1 : 0,
      canManageAdmins      ? 1 : 0,
      canAssignRights      ? 1 : 0
    ]
  );

  await logAudit(pool, req, 'Updated permissions', `${email} (${role})`);
  const [rows] = await pool.query(
    'SELECT * FROM permission_overrides WHERE email = ? LIMIT 1', [email]
  );
  return ok(res, mapOverride(rows[0]));
}));

module.exports = router;

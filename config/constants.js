// Every role gets every key explicitly (no implicit false) so a new key added
// to PERM_KEYS can't silently default to undefined/falsy for an old role.
const BLANK_PERMS = {
  canViewApplications: false, canShortlist: false, canScreenInterns: false,
  canSendNotifications: false, canManageJobs: false, canManageCriteria: false,
  canViewStaff: false, canExport: false, canViewAudit: false,
  canManageSettings: false, canGrantPermissions: false,
  canReviewJob: false, canApproveJob: false, canManageDepartments: false,
  canManageAdmins: false, canAssignRights: false,
  canScheduleAssessment: false, canRecordAssessment: false, canManageBackgroundChecks: false
};

const ROLE_DEFAULTS = {
  super: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canScreenInterns: true,
    canSendNotifications: true, canManageJobs: true, canManageCriteria: true,
    canViewStaff: true, canExport: true, canViewAudit: true,
    canManageSettings: true, canGrantPermissions: true,
    canReviewJob: true, canApproveJob: true, canManageDepartments: true,
    canManageAdmins: true, canAssignRights: true,
    canScheduleAssessment: true, canRecordAssessment: true, canManageBackgroundChecks: true
  },
  hr: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canScreenInterns: true,
    canSendNotifications: true, canManageJobs: true, canManageCriteria: true,
    canViewStaff: true, canExport: true,
    canScheduleAssessment: true, canManageBackgroundChecks: true
  },
  recruiter: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canManageCriteria: true
  },
  // CAA's hierarchical roles, layered on top of the three above.
  auditor: {
    ...BLANK_PERMS,
    canExport: true, canViewAudit: true
  },
  hr_officer: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canManageJobs: true,
    canManageCriteria: true, canSendNotifications: true,
    canScheduleAssessment: true, canManageBackgroundChecks: true
  },
  it_admin: {
    ...BLANK_PERMS,
    canAssignRights: true
  },
  dhra: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canApproveJob: true,
    canExport: true, canViewAudit: true,
    canScheduleAssessment: true, canRecordAssessment: true, canManageBackgroundChecks: true
  },
  hod: {
    ...BLANK_PERMS,
    canViewApplications: true, canShortlist: true, canReviewJob: true,
    canRecordAssessment: true
  }
};

const QUAL_LEVELS = ['O-Level', 'A-Level', 'Certificate', 'Diploma', 'Degree', 'Masters', 'PhD'];

const STATUSES = [
  'Pending', 'Under Review', 'Shortlisted', 'Interview',
  'Assessment Scheduled', 'Assessment Complete', 'Shortlisted II', 'Background Check',
  'Offered', 'Declined'
];

const ADMIN_ROLES = ['super', 'hr', 'recruiter', 'auditor', 'hr_officer', 'it_admin', 'dhra', 'hod'];

const PERM_KEYS = [
  'canViewApplications', 'canShortlist', 'canScreenInterns',
  'canSendNotifications', 'canManageJobs', 'canManageCriteria',
  'canViewStaff', 'canExport', 'canViewAudit',
  'canManageSettings', 'canGrantPermissions',
  'canReviewJob', 'canApproveJob', 'canManageDepartments',
  'canManageAdmins', 'canAssignRights',
  'canScheduleAssessment', 'canRecordAssessment', 'canManageBackgroundChecks'
];

module.exports = { ROLE_DEFAULTS, QUAL_LEVELS, STATUSES, PERM_KEYS, ADMIN_ROLES };

const ROLE_DEFAULTS = {
  super: {
    canViewApplications: true,
    canShortlist: true,
    canScreenInterns: true,
    canSendNotifications: true,
    canManageJobs: true,
    canManageCriteria: true,
    canViewStaff: true,
    canExport: true,
    canViewAudit: true,
    canManageSettings: true,
    canGrantPermissions: true
  },
  hr: {
    canViewApplications: true,
    canShortlist: true,
    canScreenInterns: true,
    canSendNotifications: true,
    canManageJobs: true,
    canManageCriteria: true,
    canViewStaff: true,
    canExport: true,
    canViewAudit: false,
    canManageSettings: false,
    canGrantPermissions: false
  },
  recruiter: {
    canViewApplications: true,
    canShortlist: true,
    canScreenInterns: false,
    canSendNotifications: false,
    canManageJobs: false,
    canManageCriteria: true,
    canViewStaff: false,
    canExport: false,
    canViewAudit: false,
    canManageSettings: false,
    canGrantPermissions: false
  }
};

const QUAL_LEVELS = ['O-Level', 'A-Level', 'Certificate', 'Diploma', 'Degree', 'Masters', 'PhD'];

const STATUSES = ['Pending', 'Under Review', 'Shortlisted', 'Interview', 'Offered', 'Declined'];

const PERM_KEYS = [
  'canViewApplications', 'canShortlist', 'canScreenInterns',
  'canSendNotifications', 'canManageJobs', 'canManageCriteria',
  'canViewStaff', 'canExport', 'canViewAudit',
  'canManageSettings', 'canGrantPermissions'
];

module.exports = { ROLE_DEFAULTS, QUAL_LEVELS, STATUSES, PERM_KEYS };

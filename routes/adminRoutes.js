// routes/adminRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const {
  listAuditLogs,
  getSettings,
  updateSetting,
  getPermissions,
  updateRolePermissions
} = require('../controllers/adminController');

const superAdminOnly = authorize('super_admin');

router.get('/audit-logs', authenticate, superAdminOnly, asyncHandler(listAuditLogs));
router.get('/settings', authenticate, superAdminOnly, asyncHandler(getSettings));
router.put('/settings/:key', authenticate, superAdminOnly, asyncHandler(updateSetting));
router.get('/permissions', authenticate, superAdminOnly, asyncHandler(getPermissions));
router.put('/permissions/:role', authenticate, superAdminOnly, asyncHandler(updateRolePermissions));

module.exports = router;

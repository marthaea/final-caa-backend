// routes/dashboardRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const {
  getSummary,
  getApplicationsByStatus,
  getApplicationsByDepartment,
  getApplicationTrend
} = require('../controllers/dashboardController');

const staffRoles = authorize('recruiter', 'hr_director', 'super_admin');

router.get('/summary', authenticate, staffRoles, asyncHandler(getSummary));
router.get('/applications-by-status', authenticate, staffRoles, asyncHandler(getApplicationsByStatus));
router.get('/applications-by-department', authenticate, staffRoles, asyncHandler(getApplicationsByDepartment));
router.get('/application-trend', authenticate, staffRoles, asyncHandler(getApplicationTrend));

module.exports = router;

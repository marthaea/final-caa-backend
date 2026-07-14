// routes/userRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const {
  getMe,
  updateMe,
  listInternalStaff,
  getUserById,
  updateUserRole,
  updateUserStatus
} = require('../controllers/userController');

router.get('/me', authenticate, asyncHandler(getMe));
router.put('/me', authenticate, asyncHandler(updateMe));

router.get('/internal', authenticate, authorize('hr_director', 'super_admin'), asyncHandler(listInternalStaff));

router.get('/:id', authenticate, authorize('super_admin'), asyncHandler(getUserById));
router.put('/:id/role', authenticate, authorize('super_admin'), asyncHandler(updateUserRole));
router.put('/:id/status', authenticate, authorize('super_admin'), asyncHandler(updateUserStatus));

module.exports = router;

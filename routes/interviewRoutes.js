// routes/interviewRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const { updateInterview } = require('../controllers/interviewController');

router.put(
  '/:id',
  authenticate,
  authorize('recruiter', 'hr_director', 'super_admin'),
  asyncHandler(updateInterview)
);

module.exports = router;

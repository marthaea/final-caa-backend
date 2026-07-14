// routes/shortlistRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const { removeShortlistEntry } = require('../controllers/shortlistController');

router.delete(
  '/:id',
  authenticate,
  authorize('recruiter', 'hr_director', 'super_admin'),
  asyncHandler(removeShortlistEntry)
);

module.exports = router;

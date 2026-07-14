// routes/offerRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const { updateOfferStatus } = require('../controllers/offerController');

router.put(
  '/:id/status',
  authenticate,
  authorize('hr_director', 'super_admin'),
  asyncHandler(updateOfferStatus)
);

module.exports = router;

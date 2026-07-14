// routes/vacancyRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize, optionalAuthenticate } = require('../middleware/auth');
const {
  listVacancies,
  getVacancy,
  createVacancy,
  updateVacancy,
  deleteVacancy,
  publishVacancy,
  closeVacancy
} = require('../controllers/vacancyController');

const { applyToVacancy } = require('../controllers/applicationController');

const hrOrAdmin = authorize('hr_director', 'super_admin');
const candidateRoles = authorize('external_candidate', 'internal_candidate');

// Public for external candidates, richer results for authenticated users
router.get('/', optionalAuthenticate, asyncHandler(listVacancies));
router.get('/:id', optionalAuthenticate, asyncHandler(getVacancy));

router.post('/', authenticate, hrOrAdmin, asyncHandler(createVacancy));
router.put('/:id', authenticate, hrOrAdmin, asyncHandler(updateVacancy));
router.delete('/:id', authenticate, hrOrAdmin, asyncHandler(deleteVacancy));
router.post('/:id/publish', authenticate, hrOrAdmin, asyncHandler(publishVacancy));
router.post('/:id/close', authenticate, hrOrAdmin, asyncHandler(closeVacancy));

router.post('/:vacancy_id/apply', authenticate, candidateRoles, asyncHandler(applyToVacancy));

module.exports = router;

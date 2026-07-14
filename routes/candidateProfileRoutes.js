// routes/candidateProfileRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const upload = require('../config/upload');
const {
  getMyProfile,
  updateMyProfile,
  uploadResume,
  uploadCoverLetter,
  getProfileById
} = require('../controllers/candidateProfileController');

const {
  listEducation,
  addEducation,
  updateEducation,
  deleteEducation
} = require('../controllers/educationController');

const {
  listCertificates,
  addCertificate,
  updateCertificate,
  deleteCertificate
} = require('../controllers/certificateController');

const {
  listReferees,
  addReferee,
  updateReferee,
  deleteReferee
} = require('../controllers/refereeController');

const candidateRoles = authorize('external_candidate', 'internal_candidate');

router.get('/me', authenticate, candidateRoles, asyncHandler(getMyProfile));
router.put('/me', authenticate, candidateRoles, asyncHandler(updateMyProfile));
router.post('/me/resume', authenticate, candidateRoles, upload.single('resume'), asyncHandler(uploadResume));
router.post('/me/cover-letter', authenticate, candidateRoles, upload.single('cover_letter'), asyncHandler(uploadCoverLetter));

router.get('/:id', authenticate, authorize('recruiter', 'hr_director', 'super_admin'), asyncHandler(getProfileById));

// Nested: education
router.get('/me/education', authenticate, candidateRoles, asyncHandler(listEducation));
router.post('/me/education', authenticate, candidateRoles, asyncHandler(addEducation));
router.put('/me/education/:id', authenticate, candidateRoles, asyncHandler(updateEducation));
router.delete('/me/education/:id', authenticate, candidateRoles, asyncHandler(deleteEducation));

// Nested: professional certificates
router.get('/me/certificates', authenticate, candidateRoles, asyncHandler(listCertificates));
router.post('/me/certificates', authenticate, candidateRoles, asyncHandler(addCertificate));
router.put('/me/certificates/:id', authenticate, candidateRoles, asyncHandler(updateCertificate));
router.delete('/me/certificates/:id', authenticate, candidateRoles, asyncHandler(deleteCertificate));

// Nested: referees
router.get('/me/referees', authenticate, candidateRoles, asyncHandler(listReferees));
router.post('/me/referees', authenticate, candidateRoles, asyncHandler(addReferee));
router.put('/me/referees/:id', authenticate, candidateRoles, asyncHandler(updateReferee));
router.delete('/me/referees/:id', authenticate, candidateRoles, asyncHandler(deleteReferee));

module.exports = router;

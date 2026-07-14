// routes/departmentRoutes.js
const express = require('express');
const router = express.Router();
const asyncHandler = require('../utils/asyncHandler');
const { authenticate, authorize } = require('../middleware/auth');
const {
  listDepartments,
  getDepartment,
  createDepartment,
  updateDepartment,
  deleteDepartment
} = require('../controllers/departmentController');

router.get('/', authenticate, asyncHandler(listDepartments));
router.get('/:id', authenticate, asyncHandler(getDepartment));
router.post('/', authenticate, authorize('hr_director', 'super_admin'), asyncHandler(createDepartment));
router.put('/:id', authenticate, authorize('hr_director', 'super_admin'), asyncHandler(updateDepartment));
router.delete('/:id', authenticate, authorize('super_admin'), asyncHandler(deleteDepartment));

module.exports = router;

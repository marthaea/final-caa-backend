const { body } = require('express-validator');

const QUAL_LEVELS  = ['O-Level','A-Level','Certificate','Diploma','Degree','Masters','PhD'];
const SALARY_BANDS = ['UG1','UG2','UG3','UG4','UG5','UG6','UG7'];

const createJobRules = [
  body('title').trim().notEmpty().withMessage('title required').isLength({ max: 255 }),
  body('dept').trim().notEmpty().withMessage('dept required').isLength({ max: 100 }),
  body('deptKey').trim().notEmpty().withMessage('deptKey required').isLength({ max: 50 }),
  body('location').trim().notEmpty().withMessage('location required').isLength({ max: 150 }),
  body('salary').trim().notEmpty().withMessage('salary required').isLength({ max: 100 }),
  body('salaryBand').isIn(SALARY_BANDS).withMessage('salaryBand must be one of UG1–UG7'),
  body('type').isIn(['Full-time','Contract']).withMessage('type must be Full-time or Contract'),
  body('closes').trim().notEmpty().withMessage('closes display string required'),
  body('closesAt').isISO8601().withMessage('closesAt must be a valid date (YYYY-MM-DD)'),
  body('visibility').isIn(['external','internal']).withMessage('visibility must be external or internal'),
  body('minAge').optional().isInt({ min: 16 }).withMessage('minAge must be an integer ≥ 16'),
  body('requiredExperience').optional().isInt({ min: 0 }).withMessage('requiredExperience must be ≥ 0'),
  body('requiredQualification').isIn(QUAL_LEVELS).withMessage(`requiredQualification must be one of: ${QUAL_LEVELS.join(', ')}`),
  body('description').optional({ checkFalsy: true }).isString().isLength({ max: 10000 }),
  body('featured').optional().isBoolean()
];

const updateJobRules = [
  body('title').optional().trim().isLength({ min: 1, max: 255 }),
  body('dept').optional().trim().isLength({ min: 1, max: 100 }),
  body('deptKey').optional().trim().isLength({ min: 1, max: 50 }),
  body('location').optional().trim().isLength({ min: 1, max: 150 }),
  body('salary').optional().trim().isLength({ min: 1, max: 100 }),
  body('salaryBand').optional().isIn(SALARY_BANDS),
  body('type').optional().isIn(['Full-time','Contract']),
  body('closesAt').optional().isISO8601().withMessage('closesAt must be a valid date'),
  body('visibility').optional().isIn(['external','internal']),
  body('minAge').optional().isInt({ min: 16 }),
  body('requiredExperience').optional().isInt({ min: 0 }),
  body('requiredQualification').optional().isIn(QUAL_LEVELS),
  body('description').optional({ checkFalsy: true }).isString().isLength({ max: 10000 }),
  body('featured').optional().isBoolean()
];

module.exports = { createJobRules, updateJobRules };

const { body } = require('express-validator');

const registerRules = [
  body('email')
    .isEmail().withMessage('Valid email required')
    .normalizeEmail(),
  body('password')
    .isLength({ min: 8 }).withMessage('Password must be at least 8 characters')
    .matches(/\d/).withMessage('Password must contain at least one number'),
  body('firstName')
    .trim().notEmpty().withMessage('First name required')
    .isLength({ max: 100 }).withMessage('First name must not exceed 100 characters'),
  body('lastName')
    .trim().notEmpty().withMessage('Last name required')
    .isLength({ max: 100 }).withMessage('Last name must not exceed 100 characters'),
  body('accountType')
    .isIn(['external', 'internal']).withMessage('accountType must be external or internal'),
  body('employeeNumber')
    .if(body('accountType').equals('internal'))
    .notEmpty().withMessage('employeeNumber is required for internal accounts')
    .trim()
];

const loginRules = [
  body('email').isEmail().withMessage('Valid email required').normalizeEmail(),
  body('password').notEmpty().withMessage('Password required')
];

const profileUpdateRules = [
  body('email').optional()
    .isEmail().withMessage('Valid email required')
    .normalizeEmail(),
  body('firstName').optional()
    .trim().isLength({ min: 1, max: 100 }).withMessage('First name must be 1–100 characters'),
  body('lastName').optional()
    .trim().isLength({ min: 1, max: 100 }).withMessage('Last name must be 1–100 characters')
];

module.exports = { registerRules, loginRules, profileUpdateRules };

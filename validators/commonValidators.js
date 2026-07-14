const { param, query, body } = require('express-validator');

const idParamRule = [
  param('id').isInt({ min: 1 }).withMessage('Invalid id parameter')
];

const jobIdParamRule = [
  param('jobId').isInt({ min: 1 }).withMessage('Invalid jobId parameter')
];

const paginationRules = [
  query('limit').optional().isInt({ min: 1, max: 1000 }).withMessage('limit must be 1–1000'),
  query('offset').optional().isInt({ min: 0 }).withMessage('offset must be ≥ 0')
];

const emailBodyRule = [
  body('email').isEmail().withMessage('Valid email required').normalizeEmail()
];

module.exports = { idParamRule, jobIdParamRule, paginationRules, emailBodyRule };

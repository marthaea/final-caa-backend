const { body } = require('express-validator');

const STATUSES = ['Pending','Under Review','Shortlisted','Interview','Offered','Declined'];

const submitApplicationRules = [
  body('jobId').isInt({ min: 1 }).withMessage('Valid jobId required'),
  body('completion').isInt({ min: 0, max: 100 }).withMessage('completion must be between 0 and 100'),
  body('cgpa').optional({ checkFalsy: true })
    .isFloat({ min: 0, max: 5 }).withMessage('cgpa must be between 0.0 and 5.0'),
  body('university').optional({ checkFalsy: true })
    .isString().isLength({ max: 255 }),
  body('screeningAnswers').optional({ checkFalsy: true })
    .isObject().withMessage('screeningAnswers must be an object')
];

const statusUpdateRules = [
  body('status').isIn(STATUSES).withMessage(`status must be one of: ${STATUSES.join(', ')}`),
  body('notifyEmail').optional({ checkFalsy: true })
    .isEmail().withMessage('notifyEmail must be a valid email address'),
  body('notifyMessage').optional({ checkFalsy: true })
    .isString().isLength({ max: 2000 })
];

const bulkStatusRules = [
  body('updates').isArray({ min: 1 }).withMessage('updates must be a non-empty array'),
  body('updates.*.id').isInt({ min: 1 }).withMessage('Each update must have a valid integer id'),
  body('updates.*.status').isIn(STATUSES)
    .withMessage(`Each status must be one of: ${STATUSES.join(', ')}`)
];

module.exports = { submitApplicationRules, statusUpdateRules, bulkStatusRules };

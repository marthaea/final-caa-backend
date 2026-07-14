const { validationResult } = require('express-validator');

// Runs after an array of express-validator checks. Responds 400 with the
// list of field errors instead of letting bad data reach the DB.
function validate(req, res, next) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({
      success: false,
      error: 'Validation failed',
      errors: errors.array().map(e => ({ field: e.path, message: e.msg }))
    });
  }
  next();
}

module.exports = validate;

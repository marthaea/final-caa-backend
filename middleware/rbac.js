const pool = require('../config/db');
const { ROLE_DEFAULTS } = require('../config/constants');
const { toSnake } = require('../utils/format');

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user || req.user.accountType !== 'admin') {
      return res.status(403).json({ success: false, error: 'Forbidden' });
    }
    if (!roles.includes(req.user.adminRole)) {
      return res.status(403).json({ success: false, error: 'Forbidden' });
    }
    next();
  };
}

function requirePerm(permKey) {
  return async (req, res, next) => {
    try {
      if (!req.user || req.user.accountType !== 'admin') {
        return res.status(403).json({ success: false, error: 'Forbidden' });
      }
      const role = req.user.adminRole;
      if (!role || !ROLE_DEFAULTS[role]) {
        return res.status(403).json({ success: false, error: 'Permission denied' });
      }
      const [rows] = await pool.query(
        'SELECT * FROM permission_overrides WHERE email = ? LIMIT 1',
        [req.user.email]
      );
      let allowed;
      if (rows.length > 0) {
        const dbKey = toSnake(permKey);
        allowed = rows[0][dbKey] === 1;
      } else {
        allowed = ROLE_DEFAULTS[role][permKey] === true;
      }
      if (!allowed) {
        return res.status(403).json({ success: false, error: 'Permission denied' });
      }
      next();
    } catch (e) {
      console.error('rbac error:', e);
      return res.status(500).json({ success: false, error: 'Internal server error' });
    }
  };
}

module.exports = { requireRole, requirePerm };

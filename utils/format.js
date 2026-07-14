const { ROLE_DEFAULTS } = require('../config/constants');

function formatDate(d) {
  return new Date(d).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric'
  });
}

function toCamel(row) {
  if (!row || typeof row !== 'object') return row;
  return Object.fromEntries(
    Object.entries(row).map(([k, v]) => [
      k.replace(/_([a-z])/g, (_, c) => c.toUpperCase()), v
    ])
  );
}

function toSnake(str) {
  return str.replace(/([A-Z])/g, '_$1').toLowerCase();
}

function ok(res, data, status = 200) {
  return res.status(status).json({ success: true, data });
}

function okList(res, data) {
  return res.json({ success: true, data, total: data.length });
}

function fail(res, message, status = 400) {
  return res.status(status).json({ success: false, error: message });
}

async function logAudit(pool, req, action, target = null) {
  try {
    const actor = `${req.user.firstName} ${req.user.lastName}`;
    const role = req.user.adminRole || req.user.accountType;
    await pool.query(
      'INSERT INTO audit_log (actor, role, action, target) VALUES (?, ?, ?, ?)',
      [actor, role, action, target]
    );
  } catch (_) {}
}

async function checkPerm(pool, user, permKey) {
  if (!user || user.accountType !== 'admin') return false;
  const role = user.adminRole;
  if (!role || !ROLE_DEFAULTS[role]) return false;
  const [rows] = await pool.query(
    'SELECT * FROM permission_overrides WHERE email = ? LIMIT 1',
    [user.email]
  );
  if (rows.length > 0) {
    const dbKey = toSnake(permKey);
    return rows[0][dbKey] === 1;
  }
  return ROLE_DEFAULTS[role][permKey] === true;
}

module.exports = { formatDate, toCamel, toSnake, ok, okList, fail, logAudit, checkPerm };

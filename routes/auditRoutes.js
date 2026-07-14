const router = require('express').Router();
const pool = require('../config/db');
const { verifyToken } = require('../middleware/auth');
const { requireRole, requirePerm } = require('../middleware/rbac');
const { ok, okList } = require('../utils/format');

function mapEntry(row) {
  return {
    id: row.id,
    at: new Date(row.created_at).toISOString(),
    actor: row.actor,
    role: row.role,
    action: row.action,
    target: row.target
  };
}

// GET /api/audit
router.get('/', verifyToken, requirePerm('canViewAudit'), async (req, res) => {
  try {
    const { search } = req.query;
    const limit = Math.min(parseInt(req.query.limit) || 200, 1000);
    const params = [];
    let sql = 'SELECT * FROM audit_log';
    if (search) {
      sql += ' WHERE actor LIKE ? OR action LIKE ? OR target LIKE ?';
      const like = `%${search}%`;
      params.push(like, like, like);
    }
    sql += ' ORDER BY created_at DESC LIMIT ?';
    params.push(limit);
    const [rows] = await pool.query(sql, params);
    return res.json({ success: true, data: rows.map(mapEntry), total: rows.length });
  } catch (e) {
    console.error('GET /audit:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// POST /api/audit
router.post('/', verifyToken, requireRole('super', 'hr', 'recruiter'), async (req, res) => {
  try {
    const { action, target } = req.body;
    if (!action) return res.status(400).json({ success: false, error: 'action is required' });

    const actor = `${req.user.firstName} ${req.user.lastName}`;
    const role  = req.user.adminRole;
    const [result] = await pool.query(
      'INSERT INTO audit_log (actor, role, action, target) VALUES (?, ?, ?, ?)',
      [actor, role, action, target || null]
    );
    const [rows] = await pool.query('SELECT * FROM audit_log WHERE id = ?', [result.insertId]);
    return ok(res, mapEntry(rows[0]), 201);
  } catch (e) {
    console.error('POST /audit:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { ok, okList, fail } = require('../utils/format');

function mapNotif(row) {
  return {
    id: row.id,
    recipientEmail: row.recipient_email,
    title: row.title,
    message: row.message,
    read: !!row.is_read,
    type: row.type,
    at: new Date(row.created_at).toISOString()
  };
}

// GET /api/notifications
router.get('/', verifyToken, asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    `SELECT * FROM notifications
     WHERE recipient_email = LOWER(?)
     ORDER BY created_at DESC
     LIMIT 100`,
    [req.user.email]
  );
  return okList(res, rows.map(mapNotif));
}));

// PUT /api/notifications/:id/read
router.put('/:id/read', verifyToken, asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM notifications WHERE id = ? LIMIT 1', [req.params.id]
  );
  if (rows.length === 0) return fail(res, 'Notification not found', 404);
  if (rows[0].recipient_email.toLowerCase() !== req.user.email.toLowerCase()) {
    return res.status(403).json({ success: false, error: 'Forbidden' });
  }
  await pool.query('UPDATE notifications SET is_read = 1 WHERE id = ?', [req.params.id]);
  return ok(res, { id: parseInt(req.params.id), isRead: true });
}));

module.exports = router;

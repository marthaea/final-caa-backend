const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail } = require('../utils/format');

const OUTCOMES = ['answered', 'suggested', 'fallback'];

// POST /api/chatbot/queries
// Public — Martha logs what visitors ask so HR can see which questions need
// new FAQ entries. No auth: guests use the chatbot too.
router.post('/queries', asyncHandler(async (req, res) => {
  const { query, matchedQuestion, outcome, persona } = req.body;
  if (!query || typeof query !== 'string') return fail(res, 'query is required');
  if (!OUTCOMES.includes(outcome)) return fail(res, 'invalid outcome');

  await pool.query(
    `INSERT INTO chatbot_queries (query, matched_question, outcome, persona)
     VALUES (?, ?, ?, ?)`,
    [
      query.slice(0, 500),
      matchedQuestion ? String(matchedQuestion).slice(0, 255) : null,
      outcome,
      persona ? String(persona).slice(0, 20) : 'guest'
    ]
  );
  return ok(res, { logged: true }, 201);
}));

// GET /api/chatbot/queries?outcome=fallback&days=30&limit=200
// Admin — powers the "Martha" panel in Site Analytics.
router.get('/queries', verifyToken, requirePerm('canViewAudit'), asyncHandler(async (req, res) => {
  const conditions = [];
  const params = [];
  if (req.query.outcome && OUTCOMES.includes(req.query.outcome)) {
    conditions.push('outcome = ?');
    params.push(req.query.outcome);
  }
  const days = Math.min(parseInt(req.query.days) || 30, 365);
  conditions.push('asked_at >= DATE_SUB(NOW(), INTERVAL ? DAY)');
  params.push(days);

  const limit = Math.min(parseInt(req.query.limit) || 200, 1000);
  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const [rows] = await pool.query(
    `SELECT id, query, matched_question, outcome, persona, asked_at
     FROM chatbot_queries ${where}
     ORDER BY asked_at DESC LIMIT ?`,
    [...params, limit]
  );
  return okList(res, rows.map(r => ({
    id: r.id,
    query: r.query,
    matchedQuestion: r.matched_question,
    outcome: r.outcome,
    persona: r.persona,
    askedAt: r.asked_at
  })));
}));

module.exports = router;

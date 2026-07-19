const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, fail } = require('../utils/format');

const VALID_TYPES = ['page_view', 'job_view', 'apply_click', 'save_job', 'search'];

// Simple in-process rate limiter: max 10 events per IP per minute
const rateLimitMap = new Map();
function rateLimit(req, res, next) {
  const ip = req.ip || req.connection.remoteAddress;
  const now = Date.now();
  const windowMs = 60000;
  const max = 10;
  const entry = rateLimitMap.get(ip) || { count: 0, start: now };
  if (now - entry.start > windowMs) {
    entry.count = 0;
    entry.start = now;
  }
  entry.count++;
  rateLimitMap.set(ip, entry);
  if (entry.count > max) {
    return res.status(429).json({ success: false, error: 'Too many requests' });
  }
  next();
}

function getCookie(req, name) {
  const cookies = req.headers.cookie || '';
  const match = cookies.split(';').find(c => c.trim().startsWith(`${name}=`));
  return match ? match.trim().split('=')[1] : null;
}

// POST /api/analytics/event  (public)
router.post('/event', rateLimit, asyncHandler(async (req, res) => {
  const { type, jobId, jobTitle, query } = req.body;
  if (!VALID_TYPES.includes(type)) {
    return fail(res, `type must be one of: ${VALID_TYPES.join(', ')}`);
  }
  const sessionId = getCookie(req, 'caa_sid') || null;
  await pool.query(
    `INSERT INTO analytics_events (event_type, job_id, job_title, query, session_id)
     VALUES (?, ?, ?, ?, ?)`,
    [type, jobId || null, jobTitle || null, query || null, sessionId]
  );
  return res.status(201).json({ success: true });
}));

// GET /api/analytics
router.get('/', verifyToken, requirePerm('canViewAudit'), asyncHandler(async (req, res) => {
  const days = Math.min(parseInt(req.query.days) || 30, 365);

  // Raw events (last N days, newest first, limit 500)
  const [events] = await pool.query(
    `SELECT * FROM analytics_events
     WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
     ORDER BY created_at DESC LIMIT 500`,
    [days]
  );

  const mappedEvents = events.map(r => ({
    id: r.id,
    type: r.event_type,
    jobId: r.job_id,
    jobTitle: r.job_title,
    query: r.query,
    ts: new Date(r.created_at).getTime(),
    sessionId: r.session_id
  }));

  // Summary: counts per event_type for last 7 days
  const [summaryRows] = await pool.query(
    `SELECT event_type, COUNT(*) AS cnt FROM analytics_events
     WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
     GROUP BY event_type`
  );
  const summaryMap = {};
  for (const r of summaryRows) summaryMap[r.event_type] = r.cnt;

  const summary = {
    pageViews7:   summaryMap['page_view']   || 0,
    jobViews7:    summaryMap['job_view']     || 0,
    applyClicks7: summaryMap['apply_click']  || 0,
    searches7:    summaryMap['search']        || 0,
    saveJobs7:    summaryMap['save_job']      || 0
  };

  // Top 5 jobs by job_view (last 7 days)
  const [topJobs] = await pool.query(
    `SELECT job_id AS jobId, job_title AS jobTitle, COUNT(*) AS count
     FROM analytics_events
     WHERE event_type = 'job_view'
       AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
       AND job_id IS NOT NULL
     GROUP BY job_id, job_title
     ORDER BY count DESC
     LIMIT 5`
  );

  // Top 10 search queries (last 7 days)
  const [topSearches] = await pool.query(
    `SELECT query, COUNT(*) AS count
     FROM analytics_events
     WHERE event_type = 'search'
       AND query IS NOT NULL
       AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
     GROUP BY query
     ORDER BY count DESC
     LIMIT 10`
  );

  // Daily counts for last 7 days
  const [dailyCounts] = await pool.query(
    `SELECT DATE(created_at) AS date, COUNT(*) AS count
     FROM analytics_events
     WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
     GROUP BY DATE(created_at)
     ORDER BY date ASC`
  );

  const mappedDaily = dailyCounts.map(r => ({
    date: r.date instanceof Date ? r.date.toISOString().slice(0, 10) : String(r.date),
    count: r.count
  }));

  return ok(res, { events: mappedEvents, summary, topJobs, topSearches, dailyCounts: mappedDaily });
}));

module.exports = router;

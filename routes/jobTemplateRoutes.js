const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, okList, fail, logAudit } = require('../utils/format');

function mapTemplate(row) {
  return {
    id: row.id,
    name: row.name,
    departmentId: row.department_id,
    sourceJobId: row.source_job_id,
    content: row.content || {},
    createdAt: row.created_at
  };
}

// GET /api/job-templates
router.get('/', verifyToken, requirePerm('canManageJobs'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query('SELECT * FROM job_templates ORDER BY name ASC');
  return okList(res, rows.map(mapTemplate));
}));

// POST /api/job-templates — save the current job-creation draft as a reusable template
router.post('/', verifyToken, requirePerm('canManageJobs'), asyncHandler(async (req, res) => {
  const { name, departmentId, sourceJobId, content } = req.body;
  if (!name || !String(name).trim()) return fail(res, 'Template name is required');
  if (!content || typeof content !== 'object') return fail(res, 'Template content is required');

  const [result] = await pool.query(
    'INSERT INTO job_templates (name, department_id, source_job_id, content, created_by) VALUES (?, ?, ?, ?, ?)',
    [name.trim(), departmentId || null, sourceJobId || null, JSON.stringify(content), req.user.id]
  );

  const [rows] = await pool.query('SELECT * FROM job_templates WHERE id = ?', [result.insertId]);
  await logAudit(pool, req, 'Saved job template', name);
  return ok(res, mapTemplate(rows[0]), 201);
}));

// DELETE /api/job-templates/:id
router.delete('/:id', verifyToken, requirePerm('canManageJobs'), asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT name FROM job_templates WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Template not found', 404);
  await pool.query('DELETE FROM job_templates WHERE id = ?', [req.params.id]);
  await logAudit(pool, req, 'Deleted job template', existing[0].name);
  return ok(res, { message: 'Deleted' });
}));

module.exports = router;

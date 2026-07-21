const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken, optionalToken } = require('../middleware/auth');
const { requirePerm, requireRole } = require('../middleware/rbac');
const { ok, okList, fail, logAudit, toCamel } = require('../utils/format');
const validate = require('../middleware/validate');
const { createJobRules, updateJobRules } = require('../validators/jobValidators');

let _settingsCache = null;
let _settingsCacheTime = 0;
async function getSettings() {
  const now = Date.now();
  if (_settingsCache && now - _settingsCacheTime < 60000) return _settingsCache;
  const [rows] = await pool.query('SELECT * FROM settings LIMIT 1');
  _settingsCache = rows[0];
  _settingsCacheTime = now;
  return _settingsCache;
}

function mapJob(row) {
  return {
    id: row.id,
    abbr: row.abbr,
    title: row.title,
    dept: row.dept,
    deptKey: row.dept_key,
    location: row.location,
    salary: row.salary,
    salaryBand: row.salary_band,
    type: row.type,
    closes: row.closes,
    closesAt: row.closes_at instanceof Date
      ? row.closes_at.toISOString().slice(0, 10)
      : String(row.closes_at),
    visibility: row.visibility,
    minAge: row.min_age,
    requiredExperience: row.required_experience,
    requiredQualification: row.required_qualification,
    description: row.description,
    featured: !!row.featured,
    status: row.status,
    departmentId: row.department_id,
    declineReason: row.decline_reason
  };
}

// GET /api/jobs
router.get('/', optionalToken, asyncHandler(async (req, res) => {
  const settings = await getSettings();
  const isAdmin = req.user && req.user.accountType === 'admin';
  const effectiveType = req.user ? (req.user.effectiveType || req.user.accountType) : 'external';

  const conditions = ['closes_at >= CURDATE()'];
  const params = [];

  // Candidates only ever see published jobs; admins see every stage of the
  // approval pipeline so the workflow tabs (Review/Approve) have data to show.
  if (!isAdmin) {
    conditions.push("status = 'published'");
  }

  if (!settings.allow_external_internal_jobs && effectiveType !== 'internal' && effectiveType !== 'admin') {
    conditions.push("visibility = 'external'");
  }

  const [rows] = await pool.query(
    `SELECT * FROM jobs WHERE ${conditions.join(' AND ')} ORDER BY featured DESC, created_at DESC`,
    params
  );
  return okList(res, rows.map(mapJob));
}));

// GET /api/jobs/:id
router.get('/:id', optionalToken, asyncHandler(async (req, res) => {
  const settings = await getSettings();
  const isAdmin = req.user && req.user.accountType === 'admin';
  const effectiveType = req.user ? (req.user.effectiveType || req.user.accountType) : 'external';

  const [rows] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (rows.length === 0) return fail(res, 'Job not found', 404);
  const job = rows[0];

  if (!isAdmin && job.status !== 'published') return fail(res, 'Job not found', 404);

  const isExpired = new Date(job.closes_at) < new Date(new Date().toDateString());
  const isInternal = job.visibility === 'internal';

  if (!settings.allow_external_internal_jobs) {
    if (isExpired) return fail(res, 'Job not found', 404);
    if (isInternal && effectiveType !== 'internal' && effectiveType !== 'admin') {
      return fail(res, 'Job not found', 404);
    }
  }

  return ok(res, mapJob(job));
}));

// POST /api/jobs
router.post('/', verifyToken, requirePerm('canManageJobs'), createJobRules, validate, asyncHandler(async (req, res) => {
  const {
    title, dept, deptKey, location, salary, salaryBand, type,
    closes, closesAt, visibility, minAge, requiredExperience,
    requiredQualification, description, featured, departmentId
  } = req.body;

  if (!title || !dept || !deptKey || !location || !salary || !salaryBand ||
      !type || !closes || !closesAt || !visibility || !requiredQualification) {
    return fail(res, 'Missing required fields');
  }

  const abbr = title.split(' ')[0].slice(0, 3).toUpperCase();

  // New listings start as a draft and go through HOD review + DHRA approval
  // before they're publicly visible (see the /submit-for-review, /review,
  // /approve, /publish routes below) — they are not immediately live.
  const [result] = await pool.query(
    `INSERT INTO jobs
       (abbr, title, dept, dept_key, location, salary, salary_band, type,
        closes, closes_at, visibility, min_age, required_experience,
        required_qualification, description, featured, created_by, status, department_id)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft', ?)`,
    [
      abbr, title, dept, deptKey, location, salary, salaryBand, type,
      closes, closesAt, visibility, minAge || 21, requiredExperience || 0,
      requiredQualification, description || null, featured ? 1 : 0, req.user.id,
      departmentId || null
    ]
  );

  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [result.insertId]);
  await logAudit(pool, req, 'Created job listing (draft)', title);
  return ok(res, mapJob(jobs[0]), 201);
}));

// PUT /api/jobs/:id
router.put('/:id', verifyToken, requirePerm('canManageJobs'), updateJobRules, validate, asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);

  const {
    title, dept, deptKey, location, salary, salaryBand, type,
    closes, closesAt, visibility, minAge, requiredExperience,
    requiredQualification, description, featured, departmentId
  } = req.body;

  await pool.query(
    `UPDATE jobs SET
      title                  = COALESCE(?, title),
      dept                   = COALESCE(?, dept),
      dept_key               = COALESCE(?, dept_key),
      location               = COALESCE(?, location),
      salary                 = COALESCE(?, salary),
      salary_band            = COALESCE(?, salary_band),
      type                   = COALESCE(?, type),
      closes                 = COALESCE(?, closes),
      closes_at              = COALESCE(?, closes_at),
      visibility             = COALESCE(?, visibility),
      min_age                = COALESCE(?, min_age),
      required_experience    = COALESCE(?, required_experience),
      required_qualification = COALESCE(?, required_qualification),
      description            = COALESCE(?, description),
      featured               = COALESCE(?, featured),
      department_id          = COALESCE(?, department_id)
     WHERE id = ?`,
    [
      title || null, dept || null, deptKey || null, location || null,
      salary || null, salaryBand || null, type || null, closes || null,
      closesAt || null, visibility || null, minAge != null ? minAge : null,
      requiredExperience != null ? requiredExperience : null,
      requiredQualification || null, description !== undefined ? description : null,
      featured != null ? (featured ? 1 : 0) : null,
      departmentId != null ? departmentId : null,
      req.params.id
    ]
  );

  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  await logAudit(pool, req, 'Updated job listing', jobs[0].title);
  return ok(res, mapJob(jobs[0]));
}));

// ── Job-approval workflow ───────────────────────────────────────────────────
// Draft → (submit-for-review) → Pending Review → (HOD /review) → Pending
// Approval → (DHRA /approve) → Published. A decline at either stage sends
// the job back to Draft with a reason. Super Admin can /publish directly
// from any status, bypassing the pipeline.

// PUT /api/jobs/:id/submit-for-review
router.put('/:id/submit-for-review', verifyToken, requirePerm('canManageJobs'), asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);
  const job = existing[0];
  if (job.status !== 'draft' && job.status !== 'declined') {
    return fail(res, 'Only a draft (or declined) job can be submitted for review');
  }
  await pool.query("UPDATE jobs SET status = 'pending_review', decline_reason = NULL WHERE id = ?", [req.params.id]);
  await logAudit(pool, req, 'Submitted job for department review', job.title);
  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  return ok(res, mapJob(jobs[0]));
}));

// PUT /api/jobs/:id/review  (Head of Department)
router.put('/:id/review', verifyToken, requirePerm('canReviewJob'), asyncHandler(async (req, res) => {
  const { approve, reason } = req.body;
  const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);
  const job = existing[0];
  if (job.status !== 'pending_review') return fail(res, 'Job is not awaiting department review');

  // canReviewJob only proves "this user is some HOD" — confirm they're the
  // HOD of *this specific job's* department (Super Admin bypasses the check).
  if (req.user.adminRole !== 'super') {
    if (!job.department_id) return fail(res, 'This job has no department assigned, so it cannot be routed for review', 409);
    const [deptRows] = await pool.query('SELECT head_user_id FROM departments WHERE id = ?', [job.department_id]);
    if (deptRows.length === 0 || deptRows[0].head_user_id !== req.user.id) {
      return fail(res, 'You are not the Head of Department for this job', 403);
    }
  }

  if (approve) {
    await pool.query("UPDATE jobs SET status = 'pending_approval', reviewed_by = ? WHERE id = ?", [req.user.id, req.params.id]);
    await logAudit(pool, req, 'Approved job at department review', job.title);
  } else {
    if (!reason || !String(reason).trim()) return fail(res, 'A reason is required to decline a job');
    await pool.query("UPDATE jobs SET status = 'draft', reviewed_by = ?, decline_reason = ? WHERE id = ?", [req.user.id, reason, req.params.id]);
    await logAudit(pool, req, 'Declined job at department review', `${job.title} — ${reason}`);
  }
  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  return ok(res, mapJob(jobs[0]));
}));

// PUT /api/jobs/:id/approve  (DHRA — final approval and publish)
router.put('/:id/approve', verifyToken, requirePerm('canApproveJob'), asyncHandler(async (req, res) => {
  const { approve, reason } = req.body;
  const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);
  const job = existing[0];
  if (job.status !== 'pending_approval') return fail(res, 'Job is not awaiting final approval');

  if (approve) {
    await pool.query("UPDATE jobs SET status = 'published', approved_by = ? WHERE id = ?", [req.user.id, req.params.id]);
    await logAudit(pool, req, 'Approved and published job listing', job.title);
  } else {
    if (!reason || !String(reason).trim()) return fail(res, 'A reason is required to decline a job');
    await pool.query("UPDATE jobs SET status = 'draft', approved_by = ?, decline_reason = ? WHERE id = ?", [req.user.id, reason, req.params.id]);
    await logAudit(pool, req, 'Declined job at final approval', `${job.title} — ${reason}`);
  }
  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  return ok(res, mapJob(jobs[0]));
}));

// PUT /api/jobs/:id/publish  (Super Admin bypass — publishes from any status)
router.put('/:id/publish', verifyToken, requireRole('super'), asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);
  await pool.query("UPDATE jobs SET status = 'published', decline_reason = NULL WHERE id = ?", [req.params.id]);
  await logAudit(pool, req, 'Published job directly (Super Admin bypass)', existing[0].title);
  const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
  return ok(res, mapJob(jobs[0]));
}));

// DELETE /api/jobs/:id
router.delete('/:id', verifyToken, requirePerm('canManageJobs'), asyncHandler(async (req, res) => {
  const [existing] = await pool.query('SELECT title FROM jobs WHERE id = ?', [req.params.id]);
  if (existing.length === 0) return fail(res, 'Job not found', 404);
  const title = existing[0].title;
  await pool.query('DELETE FROM jobs WHERE id = ?', [req.params.id]);
  await logAudit(pool, req, 'Deleted job listing', title);
  return ok(res, { message: 'Deleted' });
}));

module.exports = router;

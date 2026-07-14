const router = require('express').Router();
const pool = require('../config/db');
const { verifyToken, optionalToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
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
    featured: !!row.featured
  };
}

// GET /api/jobs
router.get('/', optionalToken, async (req, res) => {
  try {
    const settings = await getSettings();
    const effectiveType = req.user ? (req.user.effectiveType || req.user.accountType) : 'external';

    const conditions = ['closes_at >= CURDATE()'];
    const params = [];

    if (!settings.allow_external_internal_jobs && effectiveType !== 'internal' && effectiveType !== 'admin') {
      conditions.push("visibility = 'external'");
    }

    const [rows] = await pool.query(
      `SELECT * FROM jobs WHERE ${conditions.join(' AND ')} ORDER BY featured DESC, created_at DESC`,
      params
    );
    return okList(res, rows.map(mapJob));
  } catch (e) {
    console.error('GET /jobs:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// GET /api/jobs/:id
router.get('/:id', optionalToken, async (req, res) => {
  try {
    const settings = await getSettings();
    const effectiveType = req.user ? (req.user.effectiveType || req.user.accountType) : 'external';

    const [rows] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
    if (rows.length === 0) return fail(res, 'Job not found', 404);
    const job = rows[0];

    const isExpired = new Date(job.closes_at) < new Date(new Date().toDateString());
    const isInternal = job.visibility === 'internal';

    if (!settings.allow_external_internal_jobs) {
      if (isExpired) return fail(res, 'Job not found', 404);
      if (isInternal && effectiveType !== 'internal' && effectiveType !== 'admin') {
        return fail(res, 'Job not found', 404);
      }
    }

    return ok(res, mapJob(job));
  } catch (e) {
    console.error('GET /jobs/:id:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// POST /api/jobs
router.post('/', verifyToken, requirePerm('canManageJobs'), createJobRules, validate, async (req, res) => {
  try {
    const {
      title, dept, deptKey, location, salary, salaryBand, type,
      closes, closesAt, visibility, minAge, requiredExperience,
      requiredQualification, description, featured
    } = req.body;

    if (!title || !dept || !deptKey || !location || !salary || !salaryBand ||
        !type || !closes || !closesAt || !visibility || !requiredQualification) {
      return fail(res, 'Missing required fields');
    }

    const abbr = title.split(' ')[0].slice(0, 3).toUpperCase();

    const [result] = await pool.query(
      `INSERT INTO jobs
         (abbr, title, dept, dept_key, location, salary, salary_band, type,
          closes, closes_at, visibility, min_age, required_experience,
          required_qualification, description, featured, created_by)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        abbr, title, dept, deptKey, location, salary, salaryBand, type,
        closes, closesAt, visibility, minAge || 21, requiredExperience || 0,
        requiredQualification, description || null, featured ? 1 : 0, req.user.id
      ]
    );

    const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [result.insertId]);
    await logAudit(pool, req, 'Created job listing', title);
    return ok(res, mapJob(jobs[0]), 201);
  } catch (e) {
    console.error('POST /jobs:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// PUT /api/jobs/:id
router.put('/:id', verifyToken, requirePerm('canManageJobs'), updateJobRules, validate, async (req, res) => {
  try {
    const [existing] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
    if (existing.length === 0) return fail(res, 'Job not found', 404);

    const {
      title, dept, deptKey, location, salary, salaryBand, type,
      closes, closesAt, visibility, minAge, requiredExperience,
      requiredQualification, description, featured
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
        featured               = COALESCE(?, featured)
       WHERE id = ?`,
      [
        title || null, dept || null, deptKey || null, location || null,
        salary || null, salaryBand || null, type || null, closes || null,
        closesAt || null, visibility || null, minAge != null ? minAge : null,
        requiredExperience != null ? requiredExperience : null,
        requiredQualification || null, description !== undefined ? description : null,
        featured != null ? (featured ? 1 : 0) : null,
        req.params.id
      ]
    );

    const [jobs] = await pool.query('SELECT * FROM jobs WHERE id = ?', [req.params.id]);
    await logAudit(pool, req, 'Updated job listing', jobs[0].title);
    return ok(res, mapJob(jobs[0]));
  } catch (e) {
    console.error('PUT /jobs/:id:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// DELETE /api/jobs/:id
router.delete('/:id', verifyToken, requirePerm('canManageJobs'), async (req, res) => {
  try {
    const [existing] = await pool.query('SELECT title FROM jobs WHERE id = ?', [req.params.id]);
    if (existing.length === 0) return fail(res, 'Job not found', 404);
    const title = existing[0].title;
    await pool.query('DELETE FROM jobs WHERE id = ?', [req.params.id]);
    await logAudit(pool, req, 'Deleted job listing', title);
    return ok(res, { message: 'Deleted' });
  } catch (e) {
    console.error('DELETE /jobs/:id:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, fail, logAudit } = require('../utils/format');

function mapCriteria(row) {
  return {
    jobId: row.job_id,
    minCgpa: row.min_cgpa != null ? parseFloat(row.min_cgpa) : null,
    minExperienceYears: row.min_experience_years,
    requiredQualLevel: row.required_qual_level,
    requiredKeywords: row.required_keywords || [],
    disqualifyingUniversities: row.disqualifying_universities || [],
    screeningQuestions: row.screening_questions || [],
    assessmentTypes: row.assessment_types || [],
    notes: row.notes
  };
}

// GET /api/criteria/:jobId
router.get('/:jobId', verifyToken, requirePerm('canManageCriteria'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM criteria WHERE job_id = ? LIMIT 1', [req.params.jobId]
  );
  if (rows.length === 0) return ok(res, null);
  return ok(res, mapCriteria(rows[0]));
}));

// PUT /api/criteria/:jobId
router.put('/:jobId', verifyToken, requirePerm('canManageCriteria'), asyncHandler(async (req, res) => {
  const { jobId } = req.params;
  const {
    minCgpa, minExperienceYears, requiredQualLevel,
    requiredKeywords, disqualifyingUniversities, screeningQuestions, assessmentTypes, notes
  } = req.body;

  // Verify job exists
  const [jobs] = await pool.query('SELECT title FROM jobs WHERE id = ? LIMIT 1', [jobId]);
  if (jobs.length === 0) return fail(res, 'Job not found', 404);

  await pool.query(
    `INSERT INTO criteria
       (job_id, min_cgpa, min_experience_years, required_qual_level,
        required_keywords, disqualifying_universities, screening_questions, assessment_types, notes)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
       min_cgpa                 = VALUES(min_cgpa),
       min_experience_years     = VALUES(min_experience_years),
       required_qual_level      = VALUES(required_qual_level),
       required_keywords        = VALUES(required_keywords),
       disqualifying_universities = VALUES(disqualifying_universities),
       screening_questions      = VALUES(screening_questions),
       assessment_types         = VALUES(assessment_types),
       notes                    = VALUES(notes),
       updated_at               = NOW()`,
    [
      jobId,
      minCgpa != null ? minCgpa : null,
      minExperienceYears != null ? minExperienceYears : null,
      requiredQualLevel || null,
      JSON.stringify(requiredKeywords || []),
      JSON.stringify(disqualifyingUniversities || []),
      JSON.stringify(screeningQuestions || []),
      JSON.stringify(assessmentTypes || []),
      notes || null
    ]
  );

  await logAudit(pool, req, 'Updated criteria', jobs[0].title);

  const [rows] = await pool.query('SELECT * FROM criteria WHERE job_id = ?', [jobId]);
  return ok(res, mapCriteria(rows[0]));
}));

module.exports = router;

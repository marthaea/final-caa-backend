const router = require('express').Router();
const pool = require('../config/db');
const asyncHandler = require('../utils/asyncHandler');
const { verifyToken } = require('../middleware/auth');
const { requirePerm } = require('../middleware/rbac');
const { ok, fail } = require('../utils/format');
const { upload, uploadToCloudinary } = require('../utils/cloudinary');
const audit = require('../utils/audit');

function mapCv(row) {
  return {
    personal: row.personal_data,
    highestLevel: row.highest_level,
    qualifications: row.qualifications || [],
    skills: row.skills || [],
    experience: row.experience || [],
    referees: row.referees || [],
    nextOfKin: row.next_of_kin,
    photoFile: row.photo_url || null
  };
}

// GET /api/cv
router.get('/', verifyToken, asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM cv_profiles WHERE user_email = ?', [req.user.email]
  );
  if (rows.length === 0) return fail(res, 'CV not found', 404);
  return ok(res, mapCv(rows[0]));
}));

// PUT /api/cv
router.put('/', verifyToken, asyncHandler(async (req, res) => {
  const { personal, highestLevel, qualifications, skills, experience, referees, nextOfKin, photoFile } = req.body;

  await pool.query(
    `INSERT INTO cv_profiles
       (user_email, personal_data, highest_level, qualifications, skills, experience, referees, next_of_kin, photo_url)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
       personal_data  = VALUES(personal_data),
       highest_level  = VALUES(highest_level),
       qualifications = VALUES(qualifications),
       skills         = VALUES(skills),
       experience     = VALUES(experience),
       referees       = VALUES(referees),
       next_of_kin    = VALUES(next_of_kin),
       photo_url      = VALUES(photo_url),
       updated_at     = NOW()`,
    [
      req.user.email,
      JSON.stringify(personal || {}),
      highestLevel || null,
      JSON.stringify(qualifications || []),
      JSON.stringify(skills || []),
      JSON.stringify(experience || []),
      JSON.stringify(referees || []),
      JSON.stringify(nextOfKin || {}),
      photoFile || null
    ]
  );

  const [rows] = await pool.query(
    'SELECT * FROM cv_profiles WHERE user_email = ?', [req.user.email]
  );
  return ok(res, mapCv(rows[0]));
}));

// POST /api/cv/upload
// Uploads a file to Cloudinary and returns the secure URL.
// The frontend stores this URL in the appropriate CV field
// (e.g. qualifications[0].awardFile, experience[0].proofFile, or photoFile).
router.post('/upload', verifyToken, upload.single('file'), asyncHandler(async (req, res) => {
  if (!req.file) return fail(res, 'No file provided');

  const isPhoto = req.body.type === 'photo' || req.file.mimetype.startsWith('image/');
  const folder  = isPhoto ? 'caa-recruitment/photos' : 'caa-recruitment/documents';

  let result;
  try {
    result = await uploadToCloudinary(req.file.buffer, {
      folder,
      public_id: `${req.user.email.replace(/[@.]/g, '_')}_${Date.now()}`,
      // Photos: convert to WebP for efficiency; docs: keep original format
      ...(isPhoto && { transformation: [{ width: 400, height: 400, crop: 'fill', format: 'webp' }] })
    });
  } catch (e) {
    // Cloudinary config errors are common during setup — give a clearer message
    if (e.message && e.message.includes('cloudinary')) {
      return fail(res, 'File storage not configured. Set CLOUDINARY_* variables in .env', 503);
    }
    throw e;
  }

  await audit.fromReq(pool, req, audit.ACTIONS.FILE_UPLOADED, result.public_id);

  // Persist photo URL directly into cv_profiles so it survives across logins
  if (isPhoto) {
    await pool.query(
      `INSERT INTO cv_profiles
         (user_email, photo_url, personal_data, qualifications, skills, experience, referees, next_of_kin)
       VALUES (?, ?, '{}', '[]', '[]', '[]', '[]', '{}')
       ON DUPLICATE KEY UPDATE photo_url = VALUES(photo_url), updated_at = NOW()`,
      [req.user.email, result.secure_url]
    );
  }

  return ok(res, {
    url:       result.secure_url,
    publicId:  result.public_id,
    format:    result.format,
    bytes:     result.bytes
  }, 201);
}));

// GET /api/cv/by-email/:email  (admin)
router.get('/by-email/:email', verifyToken, requirePerm('canViewApplications'), asyncHandler(async (req, res) => {
  const [rows] = await pool.query(
    'SELECT * FROM cv_profiles WHERE LOWER(user_email) = LOWER(?)', [req.params.email]
  );
  if (rows.length === 0) return ok(res, null);
  return ok(res, mapCv(rows[0]));
}));

module.exports = router;

const cloudinary = require('cloudinary').v2;
const multer = require('multer');

cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key:    process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET
});

const ALLOWED_MIME_TYPES = [
  'image/jpeg', 'image/png', 'image/webp',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
];

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 }, // 5 MB
  fileFilter: (_req, file, cb) => {
    if (ALLOWED_MIME_TYPES.includes(file.mimetype)) {
      cb(null, true);
    } else {
      const err = new Error('Only JPEG, PNG, WebP, PDF, and Word files are allowed');
      err.statusCode = 400;
      cb(err);
    }
  }
});

function uploadToCloudinary(buffer, options = {}) {
  return new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream(
      { folder: 'caa-recruitment', resource_type: 'auto', ...options },
      (err, result) => { if (err) reject(err); else resolve(result); }
    );
    stream.end(buffer);
  });
}

function deleteFromCloudinary(publicId, options = {}) {
  return cloudinary.uploader.destroy(publicId, { resource_type: 'auto', ...options });
}

module.exports = { upload, uploadToCloudinary, deleteFromCloudinary, cloudinary };

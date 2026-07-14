const isProd = process.env.NODE_ENV === 'production';

function errorHandler(err, req, res, next) {
  if (!isProd) {
    console.error(err);
  } else {
    console.error(`[${new Date().toISOString()}] ${req.method} ${req.path} — ${err.code || err.name}: ${err.message}`);
  }

  // MySQL duplicate key
  if (err.code === 'ER_DUP_ENTRY') {
    return res.status(409).json({ success: false, error: 'A record with that value already exists' });
  }

  // MySQL foreign-key violation
  if (err.code === 'ER_NO_REFERENCED_ROW_2') {
    return res.status(400).json({ success: false, error: 'Referenced record does not exist' });
  }

  // JWT errors
  if (err.name === 'JsonWebTokenError') {
    return res.status(401).json({ success: false, error: 'Invalid token' });
  }
  if (err.name === 'TokenExpiredError') {
    return res.status(401).json({ success: false, error: 'Token expired' });
  }

  // Multer file-size limit
  if (err.code === 'LIMIT_FILE_SIZE') {
    return res.status(400).json({ success: false, error: 'File too large (max 5 MB)' });
  }

  const status = err.statusCode || err.status || 500;
  // Never leak raw DB / stack traces to clients in production
  const message = (status === 500 && isProd)
    ? 'Internal server error'
    : (err.message || 'Internal server error');

  res.status(status).json({ success: false, error: message });
}

module.exports = errorHandler;

// controllers/authController.js
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const pool = require('../config/db');
const { signAccessToken, signRefreshToken, verifyRefreshToken } = require('../utils/jwt');

const SALT_ROUNDS = 10;

// POST /api/v1/auth/register
async function register(req, res) {
  const { email, password, role, first_name, last_name, phone } = req.body;

  if (!email || !password || !first_name || !last_name) {
    return res.status(400).json({ status: 'error', message: 'Missing required fields' });
  }

  // Only allow self-registration as a candidate role
  const allowedRoles = ['external_candidate', 'internal_candidate'];
  const finalRole = allowedRoles.includes(role) ? role : 'external_candidate';

  const password_hash = await bcrypt.hash(password, SALT_ROUNDS);

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [userResult] = await connection.query(
      `INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)`,
      [email, password_hash, finalRole]
    );

    await connection.query(
      `INSERT INTO candidate_profiles (user_id, first_name, last_name, phone) VALUES (?, ?, ?, ?)`,
      [userResult.insertId, first_name, last_name, phone || null]
    );

    await connection.commit();

    res.status(201).json({
      status: 'ok',
      message: 'Registration successful',
      user_id: userResult.insertId
    });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// POST /api/v1/auth/login
async function login(req, res) {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ status: 'error', message: 'Email and password required' });
  }

  const [rows] = await pool.query(`SELECT * FROM users WHERE email = ?`, [email]);
  const user = rows[0];

  if (!user || !user.is_active) {
    return res.status(401).json({ status: 'error', message: 'Invalid credentials' });
  }

  const match = await bcrypt.compare(password, user.password_hash);
  if (!match) {
    return res.status(401).json({ status: 'error', message: 'Invalid credentials' });
  }

  const payload = { id: user.id, role: user.role };
  const accessToken = signAccessToken(payload);
  const refreshToken = signRefreshToken(payload);

  res.json({
    status: 'ok',
    accessToken,
    refreshToken,
    user: { id: user.id, email: user.email, role: user.role }
  });
}

// POST /api/v1/auth/refresh-token
async function refreshToken(req, res) {
  const { refreshToken } = req.body;

  if (!refreshToken) {
    return res.status(400).json({ status: 'error', message: 'Refresh token required' });
  }

  try {
    const decoded = verifyRefreshToken(refreshToken);
    const accessToken = signAccessToken({ id: decoded.id, role: decoded.role });
    res.json({ status: 'ok', accessToken });
  } catch (err) {
    return res.status(401).json({ status: 'error', message: 'Invalid or expired refresh token' });
  }
}

// POST /api/v1/auth/forgot-password
async function forgotPassword(req, res) {
  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ status: 'error', message: 'Email required' });
  }

  const [rows] = await pool.query(`SELECT id FROM users WHERE email = ?`, [email]);
  const user = rows[0];

  // Always respond success even if user not found, to avoid leaking which emails are registered
  if (!user) {
    return res.json({ status: 'ok', message: 'If that email exists, a reset link has been sent' });
  }

  const token = crypto.randomBytes(32).toString('hex');
  const expiresAt = new Date(Date.now() + 60 * 60 * 1000); // 1 hour

  await pool.query(
    `INSERT INTO password_resets (user_id, token, expires_at) VALUES (?, ?, ?)`,
    [user.id, token, expiresAt]
  );

  // TODO: send `token` via email service (Nodemailer) instead of returning it directly
  res.json({ status: 'ok', message: 'If that email exists, a reset link has been sent', reset_token: token });
}

// POST /api/v1/auth/reset-password
async function resetPassword(req, res) {
  const { token, new_password } = req.body;
  if (!token || !new_password) {
    return res.status(400).json({ status: 'error', message: 'Token and new_password required' });
  }

  const [rows] = await pool.query(
    `SELECT * FROM password_resets WHERE token = ? AND used = FALSE AND expires_at > NOW()`,
    [token]
  );
  const resetRecord = rows[0];

  if (!resetRecord) {
    return res.status(400).json({ status: 'error', message: 'Invalid or expired token' });
  }

  const password_hash = await bcrypt.hash(new_password, SALT_ROUNDS);

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    await connection.query(`UPDATE users SET password_hash = ? WHERE id = ?`, [password_hash, resetRecord.user_id]);
    await connection.query(`UPDATE password_resets SET used = TRUE WHERE id = ?`, [resetRecord.id]);
    await connection.commit();
    res.json({ status: 'ok', message: 'Password reset successful' });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

module.exports = { register, login, refreshToken, forgotPassword, resetPassword };

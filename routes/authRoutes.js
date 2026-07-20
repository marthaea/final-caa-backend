const router  = require('express').Router();
const bcrypt  = require('bcrypt');
const crypto  = require('crypto');
const pool    = require('../config/db');
const { verifyToken }                          = require('../middleware/auth');
const { authLimiter, forgotPasswordLimiter }   = require('../middleware/rateLimiter');
const validate                                 = require('../middleware/validate');
const { registerRules, loginRules, profileUpdateRules,
        forgotPasswordRules, resetPasswordRules } = require('../validators/authValidators');
const { ok, fail }                             = require('../utils/format');
const { signAccessToken, signRefreshToken,
        verifyRefreshToken }                   = require('../utils/jwt');
const asyncHandler                             = require('../utils/asyncHandler');
const audit                                    = require('../utils/audit');
const mailer                                   = require('../utils/mailer');

const REFRESH_COOKIE = 'caa_refresh';
// The frontend (Netlify) and this API (Railway/Render) live on different domains,
// so from the browser's perspective every request is cross-site. Cross-site cookies
// require SameSite=None, and browsers silently drop SameSite=None cookies that
// aren't also Secure — so both must hold in any real deployment, regardless of
// whether NODE_ENV happens to be set to 'production' there. We only relax this for
// local development, where frontend and backend share http://localhost and Secure
// cookies aren't deliverable at all.
// clearCookie must receive the same options the cookie was set with — except
// maxAge, which Express deprecates there — so the clear variant drops it
const IS_LOCAL_DEV = process.env.NODE_ENV === 'development';
const CLEAR_COOKIE_OPTIONS = {
  httpOnly: true,
  secure:   !IS_LOCAL_DEV,
  sameSite: IS_LOCAL_DEV ? 'lax' : 'none'
};
const COOKIE_OPTIONS = {
  ...CLEAR_COOKIE_OPTIONS,
  maxAge: 7 * 24 * 60 * 60 * 1000 // 7 days in ms
};

const RESET_TOKEN_TTL_MS = 60 * 60 * 1000; // reset links valid for 1 hour

// Reset tokens are stored hashed so a DB leak can't be used to take over accounts
const sha256 = (value) => crypto.createHash('sha256').update(value).digest('hex');

function frontendBase() {
  // FRONTEND_URL may be a comma-separated allowlist; links use the first entry
  return (process.env.FRONTEND_URL || 'http://localhost:3000').split(',')[0].trim();
}

function buildPayload(user) {
  return {
    id:             user.id,
    email:          user.email,
    firstName:      user.first_name,
    lastName:       user.last_name,
    accountType:    user.account_type,
    adminRole:      user.admin_role      || null,
    employeeNumber: user.employee_number || null,
    effectiveType:  user.effective_type  || user.account_type
  };
}

// tv (token_version) is checked on every refresh; bumping the column in the DB
// invalidates all outstanding refresh tokens for that user
function buildRefreshPayload(user) {
  return { id: user.id, email: user.email, tv: user.token_version || 0 };
}

// ── POST /api/auth/register ──────────────────────────────────────────────────
router.post('/register', authLimiter, registerRules, validate, asyncHandler(async (req, res) => {
  const { email, password, firstName, lastName, accountType, employeeNumber } = req.body;

  const [existing] = await pool.query(
    'SELECT id FROM users WHERE LOWER(email) = LOWER(?)', [email]
  );
  if (existing.length > 0) return fail(res, 'Email already registered', 409);

  if (accountType === 'internal') {
    const [staff] = await pool.query(
      'SELECT id FROM staff WHERE employee_number = ?', [employeeNumber]
    );
    if (staff.length === 0) return fail(res, 'Employee number not found');
  }

  const passwordHash = await bcrypt.hash(password, 12);
  const verifyTokenValue = crypto.randomBytes(32).toString('hex');
  const [result] = await pool.query(
    `INSERT INTO users
       (email, password_hash, first_name, last_name, account_type, employee_number, effective_type,
        email_verified, verify_token)
     VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)`,
    [email.toLowerCase(), passwordHash, firstName, lastName, accountType,
     accountType === 'internal' ? employeeNumber : null, accountType, verifyTokenValue]
  );

  const [users] = await pool.query('SELECT * FROM users WHERE id = ?', [result.insertId]);
  const user    = users[0];

  const token        = signAccessToken(buildPayload(user));
  const refreshToken = signRefreshToken(buildRefreshPayload(user));

  res.cookie(REFRESH_COOKIE, refreshToken, COOKIE_OPTIONS);

  // Fire welcome + verification emails — non-blocking
  const verifyUrl = `${frontendBase()}/verify-email?token=${verifyTokenValue}`;
  if (mailer.isConfigured()) {
    const welcome = mailer.welcomeEmail({ firstName, lastName, email: user.email });
    mailer.sendMail({ to: user.email, subject: welcome.subject, html: welcome.html }).catch(err =>
      console.error('[mailer] welcome email failed:', err.message)
    );
    const verification = mailer.verificationEmail({ firstName, verifyUrl });
    mailer.sendMail({ to: user.email, subject: verification.subject, html: verification.html }).catch(err =>
      console.error('[mailer] verification email failed:', err.message)
    );
  } else {
    // Dev fallback: no SMTP configured — print the link so it can be used manually
    console.log(`[mailer] SMTP not configured. Verification link for ${user.email}:\n  ${verifyUrl}`);
  }

  return ok(res, {
    id: user.id, email: user.email,
    firstName: user.first_name, lastName: user.last_name,
    accountType: user.account_type, effectiveType: user.effective_type,
    emailVerified: false,
    token
  }, 201);
}));

// ── POST /api/auth/login ─────────────────────────────────────────────────────
router.post('/login', authLimiter, loginRules, validate, asyncHandler(async (req, res) => {
  const { email, password } = req.body;

  const [rows] = await pool.query(
    'SELECT * FROM users WHERE LOWER(email) = LOWER(?)', [email]
  );
  const user = rows[0];
  if (!user) return fail(res, 'Invalid credentials', 401);

  const match = await bcrypt.compare(password, user.password_hash);
  if (!match) return fail(res, 'Invalid credentials', 401);

  if (!user.is_active) return fail(res, 'This account has been deactivated. Contact support.', 403);

  const token        = signAccessToken(buildPayload(user));
  const refreshToken = signRefreshToken(buildRefreshPayload(user));

  res.cookie(REFRESH_COOKIE, refreshToken, COOKIE_OPTIONS);

  // Fire-and-forget — the client shouldn't wait on an audit-log write to get its
  // token back. Matches how welcome/verification emails are handled just above.
  audit.log(pool, {
    actor:  `${user.first_name} ${user.last_name}`,
    role:   user.admin_role || user.account_type,
    action: audit.ACTIONS.USER_LOGGED_IN,
    target: user.email
  }).catch(err => console.error('[audit] login log failed:', err.message));

  return ok(res, {
    id: user.id, email: user.email,
    firstName: user.first_name, lastName: user.last_name,
    accountType: user.account_type,
    effectiveType: user.effective_type || user.account_type,
    adminRole: user.admin_role || null,
    employeeNumber: user.employee_number || null,
    emailVerified: user.email_verified !== 0,
    token
  });
}));

// ── POST /api/auth/refresh-token ─────────────────────────────────────────────
// Reads the httpOnly cookie, issues a new access token, and rotates the
// refresh token (a new cookie replaces the old one every call).
router.post('/refresh-token', asyncHandler(async (req, res) => {
  const refreshToken = req.cookies[REFRESH_COOKIE];
  if (!refreshToken) return fail(res, 'No refresh token', 401);

  let decoded;
  try {
    decoded = verifyRefreshToken(refreshToken);
  } catch (_) {
    res.clearCookie(REFRESH_COOKIE, CLEAR_COOKIE_OPTIONS);
    return fail(res, 'Invalid or expired refresh token', 401);
  }

  const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [decoded.id]);
  if (rows.length === 0) return fail(res, 'User not found', 401);
  const user = rows[0];

  if (!user.is_active) {
    res.clearCookie(REFRESH_COOKIE, CLEAR_COOKIE_OPTIONS);
    return fail(res, 'This account has been deactivated. Contact support.', 403);
  }

  // Token issued before the last logout / password change — force re-login
  if ((decoded.tv || 0) !== (user.token_version || 0)) {
    res.clearCookie(REFRESH_COOKIE, CLEAR_COOKIE_OPTIONS);
    return fail(res, 'Session expired. Please log in again.', 401);
  }

  const newAccessToken  = signAccessToken(buildPayload(user));
  const newRefreshToken = signRefreshToken(buildRefreshPayload(user));

  res.cookie(REFRESH_COOKIE, newRefreshToken, COOKIE_OPTIONS);
  return ok(res, { token: newAccessToken });
}));

// ── GET /api/auth/me ─────────────────────────────────────────────────────────
router.get('/me', verifyToken, (req, res) => {
  return ok(res, {
    id:           req.user.id,
    email:        req.user.email,
    firstName:    req.user.firstName,
    lastName:     req.user.lastName,
    accountType:  req.user.accountType,
    effectiveType: req.user.effectiveType,
    adminRole:    req.user.adminRole || null
  });
});

// ── PUT /api/auth/profile ────────────────────────────────────────────────────
router.put('/profile', verifyToken, profileUpdateRules, validate, asyncHandler(async (req, res) => {
  const { firstName, lastName, email } = req.body;
  if (email) {
    const [existing] = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = LOWER(?) AND id != ?',
      [email, req.user.id]
    );
    if (existing.length > 0) return fail(res, 'Email already taken', 409);
  }
  await pool.query(
    `UPDATE users SET
       first_name = COALESCE(?, first_name),
       last_name  = COALESCE(?, last_name),
       email      = COALESCE(?, email)
     WHERE id = ?`,
    [firstName || null, lastName || null, email ? email.toLowerCase() : null, req.user.id]
  );
  const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [req.user.id]);
  const u = rows[0];
  return ok(res, {
    id: u.id, email: u.email,
    firstName: u.first_name, lastName: u.last_name,
    accountType: u.account_type
  });
}));

// ── GET /api/auth/verify-email?token=... ─────────────────────────────────────
// Public: called from the link in the verification email.
router.get('/verify-email', asyncHandler(async (req, res) => {
  const { token } = req.query;
  if (!token || typeof token !== 'string' || token.length !== 64) {
    return fail(res, 'Invalid verification link');
  }
  const [rows] = await pool.query('SELECT id, email FROM users WHERE verify_token = ?', [token]);
  if (rows.length === 0) return fail(res, 'This verification link is invalid or has already been used', 404);
  await pool.query('UPDATE users SET email_verified = 1, verify_token = NULL WHERE id = ?', [rows[0].id]);
  return ok(res, { message: 'Email verified', email: rows[0].email });
}));

// ── POST /api/auth/resend-verification ───────────────────────────────────────
router.post('/resend-verification', verifyToken, authLimiter, asyncHandler(async (req, res) => {
  const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [req.user.id]);
  if (rows.length === 0) return fail(res, 'User not found', 404);
  const user = rows[0];
  if (user.email_verified) return ok(res, { message: 'Email already verified' });

  let tokenValue = user.verify_token;
  if (!tokenValue) {
    tokenValue = crypto.randomBytes(32).toString('hex');
    await pool.query('UPDATE users SET verify_token = ? WHERE id = ?', [tokenValue, user.id]);
  }
  const verifyUrl = `${frontendBase()}/verify-email?token=${tokenValue}`;
  if (mailer.isConfigured()) {
    const { subject, html } = mailer.verificationEmail({ firstName: user.first_name, verifyUrl });
    await mailer.sendMail({ to: user.email, subject, html });
    return ok(res, { message: 'Verification email sent' });
  }
  console.log(`[mailer] SMTP not configured. Verification link for ${user.email}:\n  ${verifyUrl}`);
  return ok(res, { message: 'Email service is not configured yet — the verification link was logged on the server. Contact support if you need help verifying.' });
}));

// ── POST /api/auth/forgot-password ───────────────────────────────────────────
// Public. Always responds with the same message so the endpoint can't be used
// to probe which emails are registered.
router.post('/forgot-password', forgotPasswordLimiter, forgotPasswordRules, validate, asyncHandler(async (req, res) => {
  const { email } = req.body;
  const NEUTRAL = 'If that email is registered, a password reset link has been sent';

  const [rows] = await pool.query(
    'SELECT * FROM users WHERE LOWER(email) = LOWER(?)', [email]
  );
  const user = rows[0];
  if (!user || !user.is_active) return ok(res, { message: NEUTRAL });

  const tokenValue = crypto.randomBytes(32).toString('hex');
  const expiresAt  = new Date(Date.now() + RESET_TOKEN_TTL_MS);
  await pool.query(
    'UPDATE users SET reset_token_hash = ?, reset_token_expires = ? WHERE id = ?',
    [sha256(tokenValue), expiresAt, user.id]
  );

  const resetUrl = `${frontendBase()}/reset-password?token=${tokenValue}`;
  if (mailer.isConfigured()) {
    const { subject, html } = mailer.passwordResetEmail({ firstName: user.first_name, resetUrl });
    mailer.sendMail({ to: user.email, subject, html }).catch(err =>
      console.error('[mailer] password reset email failed:', err.message)
    );
  } else {
    // Dev fallback: no SMTP configured — print the link so it can be used manually
    console.log(`[mailer] SMTP not configured. Password reset link for ${user.email}:\n  ${resetUrl}`);
  }

  return ok(res, { message: NEUTRAL });
}));

// ── POST /api/auth/reset-password ────────────────────────────────────────────
// Public: called from the link in the reset email. Consumes the token and
// invalidates every outstanding refresh token for the account.
router.post('/reset-password', authLimiter, resetPasswordRules, validate, asyncHandler(async (req, res) => {
  const { token, password } = req.body;

  const [rows] = await pool.query(
    'SELECT * FROM users WHERE reset_token_hash = ? AND reset_token_expires > NOW()',
    [sha256(token)]
  );
  const user = rows[0];
  if (!user) return fail(res, 'This reset link is invalid or has expired. Please request a new one.');

  const passwordHash = await bcrypt.hash(password, 12);
  await pool.query(
    `UPDATE users SET
       password_hash = ?,
       reset_token_hash = NULL,
       reset_token_expires = NULL,
       token_version = token_version + 1
     WHERE id = ?`,
    [passwordHash, user.id]
  );

  await audit.log(pool, {
    actor:  `${user.first_name} ${user.last_name}`,
    role:   user.admin_role || user.account_type,
    action: audit.ACTIONS.PASSWORD_RESET,
    target: user.email
  });

  return ok(res, { message: 'Password reset successful. You can now log in with your new password.' });
}));

// ── POST /api/auth/logout ────────────────────────────────────────────────────
// Bumps token_version so the (rotated) refresh token can never be replayed,
// then clears the cookie.
router.post('/logout', verifyToken, asyncHandler(async (req, res) => {
  await pool.query('UPDATE users SET token_version = token_version + 1 WHERE id = ?', [req.user.id]);
  res.clearCookie(REFRESH_COOKIE, CLEAR_COOKIE_OPTIONS);
  return ok(res, { message: 'Logged out' });
}));

module.exports = router;

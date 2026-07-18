const router  = require('express').Router();
const bcrypt  = require('bcrypt');
const crypto  = require('crypto');
const pool    = require('../config/db');
const { verifyToken }                          = require('../middleware/auth');
const { authLimiter }                          = require('../middleware/rateLimiter');
const validate                                 = require('../middleware/validate');
const { registerRules, loginRules,
        profileUpdateRules }                   = require('../validators/authValidators');
const { ok, fail }                             = require('../utils/format');
const { signAccessToken, signRefreshToken,
        verifyRefreshToken }                   = require('../utils/jwt');
const audit                                    = require('../utils/audit');
const mailer                                   = require('../utils/mailer');

const REFRESH_COOKIE = 'caa_refresh';
const COOKIE_OPTIONS = {
  httpOnly: true,
  secure:   process.env.NODE_ENV === 'production', // HTTPS-only in prod
  sameSite: 'strict',
  maxAge:   7 * 24 * 60 * 60 * 1000 // 7 days in ms
};

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

// ── POST /api/auth/register ──────────────────────────────────────────────────
router.post('/register', authLimiter, registerRules, validate, async (req, res) => {
  try {
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
    const payload = buildPayload(user);

    const token        = signAccessToken(payload);
    const refreshToken = signRefreshToken({ id: user.id, email: user.email });

    res.cookie(REFRESH_COOKIE, refreshToken, COOKIE_OPTIONS);

    // Fire welcome + verification emails — non-blocking.
    // FRONTEND_URL may be a comma-separated allowlist; links use the first entry.
    const frontendBase = (process.env.FRONTEND_URL || 'http://localhost:3000').split(',')[0].trim();
    const verifyUrl = `${frontendBase}/verify-email?token=${verifyTokenValue}`;
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
  } catch (e) {
    console.error('register:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// ── POST /api/auth/login ─────────────────────────────────────────────────────
router.post('/login', authLimiter, loginRules, validate, async (req, res) => {
  try {
    const { email, password } = req.body;

    const [rows] = await pool.query(
      'SELECT * FROM users WHERE LOWER(email) = LOWER(?)', [email]
    );
    const user = rows[0];
    if (!user) return fail(res, 'Invalid credentials', 401);

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return fail(res, 'Invalid credentials', 401);

    const payload      = buildPayload(user);
    const token        = signAccessToken(payload);
    const refreshToken = signRefreshToken({ id: user.id, email: user.email });

    res.cookie(REFRESH_COOKIE, refreshToken, COOKIE_OPTIONS);

    await audit.log(pool, {
      actor:  `${user.first_name} ${user.last_name}`,
      role:   user.admin_role || user.account_type,
      action: audit.ACTIONS.USER_LOGGED_IN,
      target: user.email
    });

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
  } catch (e) {
    console.error('login:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// ── POST /api/auth/refresh-token ─────────────────────────────────────────────
// Reads the httpOnly cookie, issues a new access token, and rotates the
// refresh token (a new cookie replaces the old one every call).
router.post('/refresh-token', async (req, res) => {
  try {
    const refreshToken = req.cookies[REFRESH_COOKIE];
    if (!refreshToken) return fail(res, 'No refresh token', 401);

    let decoded;
    try {
      decoded = verifyRefreshToken(refreshToken);
    } catch (_) {
      res.clearCookie(REFRESH_COOKIE, COOKIE_OPTIONS);
      return fail(res, 'Invalid or expired refresh token', 401);
    }

    const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [decoded.id]);
    if (rows.length === 0) return fail(res, 'User not found', 401);
    const user = rows[0];

    const newAccessToken  = signAccessToken(buildPayload(user));
    const newRefreshToken = signRefreshToken({ id: user.id, email: user.email });

    res.cookie(REFRESH_COOKIE, newRefreshToken, COOKIE_OPTIONS);
    return ok(res, { token: newAccessToken });
  } catch (e) {
    console.error('refresh-token:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

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
router.put('/profile', verifyToken, profileUpdateRules, validate, async (req, res) => {
  try {
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
  } catch (e) {
    console.error('profile:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// ── GET /api/auth/verify-email?token=... ─────────────────────────────────────
// Public: called from the link in the verification email.
router.get('/verify-email', async (req, res) => {
  try {
    const { token } = req.query;
    if (!token || typeof token !== 'string' || token.length !== 64) {
      return fail(res, 'Invalid verification link');
    }
    const [rows] = await pool.query('SELECT id, email FROM users WHERE verify_token = ?', [token]);
    if (rows.length === 0) return fail(res, 'This verification link is invalid or has already been used', 404);
    await pool.query('UPDATE users SET email_verified = 1, verify_token = NULL WHERE id = ?', [rows[0].id]);
    return ok(res, { message: 'Email verified', email: rows[0].email });
  } catch (e) {
    console.error('verify-email:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// ── POST /api/auth/resend-verification ───────────────────────────────────────
router.post('/resend-verification', verifyToken, authLimiter, async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT * FROM users WHERE id = ?', [req.user.id]);
    if (rows.length === 0) return fail(res, 'User not found', 404);
    const user = rows[0];
    if (user.email_verified) return ok(res, { message: 'Email already verified' });

    let tokenValue = user.verify_token;
    if (!tokenValue) {
      tokenValue = crypto.randomBytes(32).toString('hex');
      await pool.query('UPDATE users SET verify_token = ? WHERE id = ?', [tokenValue, user.id]);
    }
    const frontendBase = (process.env.FRONTEND_URL || 'http://localhost:3000').split(',')[0].trim();
    const verifyUrl = `${frontendBase}/verify-email?token=${tokenValue}`;
    if (mailer.isConfigured()) {
      const { subject, html } = mailer.verificationEmail({ firstName: user.first_name, verifyUrl });
      await mailer.sendMail({ to: user.email, subject, html });
      return ok(res, { message: 'Verification email sent' });
    }
    console.log(`[mailer] SMTP not configured. Verification link for ${user.email}:\n  ${verifyUrl}`);
    return ok(res, { message: 'Email service is not configured yet — the verification link was logged on the server. Contact support if you need help verifying.' });
  } catch (e) {
    console.error('resend-verification:', e);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

// ── POST /api/auth/logout ────────────────────────────────────────────────────
router.post('/logout', verifyToken, (req, res) => {
  res.clearCookie(REFRESH_COOKIE, COOKIE_OPTIONS);
  return ok(res, { message: 'Logged out' });
});

module.exports = router;

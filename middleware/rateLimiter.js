const rateLimit = require('express-rate-limit');

// 10 attempts per 15 min — slows credential-stuffing without blocking typos
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { success: false, error: 'Too many attempts. Please try again in 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false
});

// 3 requests per hour — prevents inbox-spam abuse of the reset flow
const forgotPasswordLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 3,
  message: { success: false, error: 'Too many password reset requests. Please try again later.' },
  standardHeaders: true,
  legacyHeaders: false
});

// 300 requests per 15 min per IP across the whole API
const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 300,
  message: { success: false, error: 'Too many requests. Please slow down.' },
  standardHeaders: true,
  legacyHeaders: false
});

module.exports = { authLimiter, forgotPasswordLimiter, generalLimiter };
